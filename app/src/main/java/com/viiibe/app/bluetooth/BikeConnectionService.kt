package com.viiibe.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.viiibe.app.data.model.BikeConnectionState
import com.viiibe.app.data.model.RideMetrics
import com.viiibe.app.notification.MetricsNotificationManager
import com.viiibe.app.overlay.FloatingMetricsOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class BikeConnectionService : Service() {

    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(BikeConnectionState())
    val connectionState: StateFlow<BikeConnectionState> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    // Cumulative values for the ride
    private var totalOutput: Int = 0
    private var totalDistance: Float = 0f
    private var totalCalories: Int = 0
    private var rideStartTime: Long = 0

    // Overlay mode
    private var notificationManager: MetricsNotificationManager? = null
    private var floatingOverlay: FloatingMetricsOverlay? = null
    private var overlayJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isOverlayMode = false
    private var overlayElapsedSeconds = 0
    private var isWorkoutPaused = false

    inner class LocalBinder : Binder() {
        fun getService(): BikeConnectionService = this@BikeConnectionService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        notificationManager = MetricsNotificationManager(this)
        floatingOverlay = FloatingMetricsOverlay(this).also { overlay ->
            overlay.onPauseChanged = { paused ->
                isWorkoutPaused = paused
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OVERLAY -> {
                val elapsedSeconds = intent.getIntExtra(EXTRA_ELAPSED_SECONDS, 0)
                val isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                val metrics = extractMetricsFromIntent(intent)
                _metrics.value = metrics
                startOverlayMode(elapsedSeconds, isPaused)
            }
            ACTION_STOP_OVERLAY -> {
                stopOverlayMode()
            }
            ACTION_UPDATE_OVERLAY_METRICS -> {
                if (isOverlayMode) {
                    val metrics = extractMetricsFromIntent(intent)
                    _metrics.value = metrics
                }
            }
        }
        return START_STICKY
    }

    private fun extractMetricsFromIntent(intent: Intent): RideMetrics {
        return RideMetrics(
            power = intent.getIntExtra(EXTRA_POWER, _metrics.value.power),
            cadence = intent.getIntExtra(EXTRA_CADENCE, _metrics.value.cadence),
            resistance = intent.getIntExtra(EXTRA_RESISTANCE, _metrics.value.resistance),
            heartRate = intent.getIntExtra(EXTRA_HEART_RATE, _metrics.value.heartRate),
            calories = intent.getIntExtra(EXTRA_CALORIES, _metrics.value.calories),
            speed = intent.getFloatExtra(EXTRA_SPEED, _metrics.value.speed),
            distance = intent.getFloatExtra(EXTRA_DISTANCE, _metrics.value.distance),
            totalOutput = _metrics.value.totalOutput,
            elapsedSeconds = _metrics.value.elapsedSeconds
        )
    }

    fun startOverlayMode(elapsedSeconds: Int, isPaused: Boolean = false) {
        if (isOverlayMode) return

        isOverlayMode = true
        overlayElapsedSeconds = elapsedSeconds
        isWorkoutPaused = isPaused

        // Start foreground service with notification
        val notification = notificationManager?.buildNotification(_metrics.value, overlayElapsedSeconds)
        if (notification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    MetricsNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(MetricsNotificationManager.NOTIFICATION_ID, notification)
            }
        }

        // Show floating overlay
        try {
            floatingOverlay?.show(isPaused)
        } catch (e: Exception) {
            // May fail if overlay permission not granted
            android.util.Log.w(TAG, "Could not show floating overlay: ${e.message}")
        }

        // Start timer to update overlay and notification
        overlayJob = serviceScope.launch {
            while (isActive && isOverlayMode) {
                delay(1000)
                if (!isWorkoutPaused) {
                    overlayElapsedSeconds++
                }
                notificationManager?.updateNotification(_metrics.value, overlayElapsedSeconds)
                floatingOverlay?.updateMetrics(_metrics.value, overlayElapsedSeconds)
            }
        }
    }

    fun stopOverlayMode() {
        if (!isOverlayMode) return

        isOverlayMode = false
        overlayJob?.cancel()
        overlayJob = null

        // Hide floating overlay
        floatingOverlay?.hide()

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager?.cancelNotification()
    }

    fun isInOverlayMode(): Boolean = isOverlayMode

    fun getOverlayElapsedSeconds(): Int = overlayElapsedSeconds

    fun isWorkoutPaused(): Boolean = isWorkoutPaused

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBluetoothPermissions()) {
            _connectionState.value = _connectionState.value.copy(
                error = "Bluetooth permissions not granted"
            )
            return
        }

        _connectionState.value = _connectionState.value.copy(
            isScanning = true,
            error = null
        )

        // Scan for FTMS (Fitness Machine Service) devices
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)

        // Also do a general scan to find Peloton-specific devices
        bluetoothLeScanner?.startScan(generalScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (hasBluetoothPermissions()) {
            bluetoothLeScanner?.stopScan(scanCallback)
            bluetoothLeScanner?.stopScan(generalScanCallback)
        }
        _connectionState.value = _connectionState.value.copy(isScanning = false)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScanning()
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BikeConnectionState()
    }

    fun resetRideMetrics() {
        totalOutput = 0
        totalDistance = 0f
        totalCalories = 0
        rideStartTime = System.currentTimeMillis()
        _metrics.value = RideMetrics()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"

            Log.d(TAG, "Found FTMS device: $deviceName")

            // Auto-connect to first FTMS device found (likely the Peloton)
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = _connectionState.value.copy(
                isScanning = false,
                error = "Scan failed with error: $errorCode"
            )
        }
    }

    private val generalScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return

            // Look for Peloton-specific device names
            if (deviceName.contains("Peloton", ignoreCase = true) ||
                deviceName.contains("KICKR", ignoreCase = true) ||
                deviceName.contains("Bike", ignoreCase = true)
            ) {
                Log.d(TAG, "Found potential bike: $deviceName")
                connectToDevice(device)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = _connectionState.value.copy(
                        isConnected = true,
                        deviceName = gatt.device.name ?: "Bike"
                    )
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BikeConnectionState(
                        error = if (status != BluetoothGatt.GATT_SUCCESS) "Connection lost" else null
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Subscribe to FTMS Indoor Bike Data characteristic
                val ftmsService = gatt.getService(FTMS_SERVICE_UUID)
                ftmsService?.let { service ->
                    val indoorBikeData = service.getCharacteristic(INDOOR_BIKE_DATA_UUID)
                    indoorBikeData?.let { characteristic ->
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }

                // Subscribe to Heart Rate if available
                val heartRateService = gatt.getService(HEART_RATE_SERVICE_UUID)
                heartRateService?.let { service ->
                    val heartRateChar = service.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                    heartRateChar?.let { characteristic ->
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }

                // Subscribe to Cycling Power if available
                val powerService = gatt.getService(CYCLING_POWER_SERVICE_UUID)
                powerService?.let { service ->
                    val powerChar = service.getCharacteristic(CYCLING_POWER_MEASUREMENT_UUID)
                    powerChar?.let { characteristic ->
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                INDOOR_BIKE_DATA_UUID -> parseIndoorBikeData(characteristic.value)
                HEART_RATE_MEASUREMENT_UUID -> parseHeartRate(characteristic.value)
                CYCLING_POWER_MEASUREMENT_UUID -> parsePowerData(characteristic.value)
            }
        }
    }

    private fun parseIndoorBikeData(data: ByteArray) {
        if (data.size < 2) return

        // Parse flags (first 2 bytes)
        val flags = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)

        var offset = 2
        var cadence = _metrics.value.cadence
        var speed = _metrics.value.speed
        var power = _metrics.value.power
        var resistance = _metrics.value.resistance
        var distance = _metrics.value.distance

        // Instantaneous Speed (if present - bit 0 = 0 means present)
        if ((flags and 0x01) == 0 && data.size >= offset + 2) {
            val rawSpeed = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            speed = rawSpeed / 100f * 0.621371f // Convert km/h to mph
            offset += 2
        }

        // Average Speed (bit 1)
        if ((flags and 0x02) != 0) offset += 2

        // Instantaneous Cadence (bit 2)
        if ((flags and 0x04) != 0 && data.size >= offset + 2) {
            cadence = ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)) / 2
            offset += 2
        }

        // Average Cadence (bit 3)
        if ((flags and 0x08) != 0) offset += 2

        // Total Distance (bit 4)
        if ((flags and 0x10) != 0 && data.size >= offset + 3) {
            val rawDistance = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16)
            distance = rawDistance / 1000f * 0.621371f // Convert meters to miles
            offset += 3
        }

        // Resistance Level (bit 5)
        if ((flags and 0x20) != 0 && data.size >= offset + 2) {
            resistance = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
        }

        // Instantaneous Power (bit 6)
        if ((flags and 0x40) != 0 && data.size >= offset + 2) {
            val rawPower = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            power = rawPower / 10 // Peloton reports power * 10
            offset += 2
        }

        // Calculate elapsed time
        val elapsedSeconds = if (rideStartTime > 0) {
            ((System.currentTimeMillis() - rideStartTime) / 1000).toInt()
        } else 0

        // Calculate cumulative output (kJ) - power in watts * time in seconds / 1000
        if (power > 0) {
            totalOutput += power / 1000 // Rough approximation per update
        }

        // Calculate calories (rough approximation)
        totalCalories = (totalOutput * 0.239f).toInt()

        _metrics.value = RideMetrics(
            cadence = cadence,
            resistance = resistance,
            power = power,
            speed = speed,
            heartRate = _metrics.value.heartRate,
            distance = distance,
            totalOutput = totalOutput,
            calories = totalCalories,
            elapsedSeconds = elapsedSeconds
        )
    }

    private fun parseHeartRate(data: ByteArray) {
        if (data.isEmpty()) return

        val flags = data[0].toInt() and 0xFF
        val heartRate = if ((flags and 0x01) != 0) {
            // 16-bit heart rate
            if (data.size >= 3) {
                (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
            } else 0
        } else {
            // 8-bit heart rate
            if (data.size >= 2) data[1].toInt() and 0xFF else 0
        }

        _metrics.value = _metrics.value.copy(heartRate = heartRate)
    }

    private fun parsePowerData(data: ByteArray) {
        if (data.size < 4) return

        // Instantaneous Power is at bytes 2-3
        val power = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)

        _metrics.value = _metrics.value.copy(power = power)
    }

    override fun onDestroy() {
        stopOverlayMode()
        serviceScope.cancel()
        disconnect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BikeConnectionService"

        // Action constants for overlay mode
        const val ACTION_START_OVERLAY = "com.viiibe.app.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.viiibe.app.ACTION_STOP_OVERLAY"
        const val ACTION_UPDATE_OVERLAY_METRICS = "com.viiibe.app.ACTION_UPDATE_OVERLAY_METRICS"
        const val EXTRA_ELAPSED_SECONDS = "elapsed_seconds"
        const val EXTRA_IS_PAUSED = "is_paused"
        const val EXTRA_POWER = "power"
        const val EXTRA_CADENCE = "cadence"
        const val EXTRA_RESISTANCE = "resistance"
        const val EXTRA_HEART_RATE = "heart_rate"
        const val EXTRA_CALORIES = "calories"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_DISTANCE = "distance"

        // FTMS (Fitness Machine Service) UUIDs
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")
        val FITNESS_MACHINE_FEATURE_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb")

        // Heart Rate Service UUIDs
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

        // Cycling Power Service UUIDs
        val CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val CYCLING_POWER_MEASUREMENT_UUID: UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb")

        // Client Characteristic Configuration Descriptor
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
