package com.viiibe.app.bluetooth

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.data.model.BikeConnectionState
import com.viiibe.app.data.model.RideMetrics
import com.viiibe.app.sensor.PelotonSensorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BluetoothViewModel"
        // Speed calculation: at 80 cadence, approximately 18-20 mph on Peloton
        private const val CADENCE_TO_SPEED_FACTOR = 0.228f
        // Conversion constants
        private const val MPH_TO_KMH = 1.60934f
        private const val MILES_TO_KM = 1.60934f
        // Update interval in seconds (how often we sample for accumulation)
        private const val SAMPLE_INTERVAL_SECONDS = 1
    }

    private var bikeService: BikeConnectionService? = null
    private var bound = false

    // Peloton internal sensor service (for when running on Peloton hardware)
    private val pelotonSensorService = PelotonSensorService(application)
    private var usePelotonSensors = false

    private val _connectionState = MutableStateFlow(BikeConnectionState())
    val connectionState: StateFlow<BikeConnectionState> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(RideMetrics())
    val metrics: StateFlow<RideMetrics> = _metrics.asStateFlow()

    // Accumulated values for derived metrics
    private var accumulatedOutputJoules: Long = 0L  // Joules (watts * seconds)
    private var accumulatedDistanceMiles: Float = 0f
    private var lastUpdateTimeMs: Long = 0L
    private var isRideActive: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BikeConnectionService.LocalBinder
            bikeService = binder.getService()
            bound = true

            // Collect state from service
            viewModelScope.launch {
                bikeService?.connectionState?.collect { state ->
                    _connectionState.value = state
                }
            }

            viewModelScope.launch {
                bikeService?.metrics?.collect { metrics ->
                    _metrics.value = metrics
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bikeService = null
            bound = false
        }
    }

    init {
        bindService()
        observePelotonSensors()

        // Automatically connect to bike sensors on app launch
        viewModelScope.launch {
            // Small delay to ensure services are ready
            kotlinx.coroutines.delay(500)
            Log.i(TAG, "Auto-connecting to bike sensors...")
            startScanning()
        }
    }

    private fun bindService() {
        val context = getApplication<Application>()
        Intent(context, BikeConnectionService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Observe Peloton sensor service state and metrics
     */
    private fun observePelotonSensors() {
        // Observe connection state
        viewModelScope.launch {
            pelotonSensorService.isConnected.collect { connected ->
                if (connected && usePelotonSensors) {
                    _connectionState.value = BikeConnectionState(
                        isConnected = true,
                        isScanning = false,
                        deviceName = "Peloton Bike",
                        error = null
                    )
                }
            }
        }

        // Observe error state
        viewModelScope.launch {
            pelotonSensorService.error.collect { error ->
                if (error != null && usePelotonSensors) {
                    Log.w(TAG, "Peloton sensor error: $error")
                    // Fall back to Bluetooth scanning
                    usePelotonSensors = false
                    bikeService?.startScanning()
                }
            }
        }

        // Combine sensor values into metrics
        viewModelScope.launch {
            combine(
                pelotonSensorService.cadence,
                pelotonSensorService.power,
                pelotonSensorService.resistance
            ) { cadence, power, resistance ->
                Triple(cadence, power, resistance)
            }.collect { (cadence, power, resistance) ->
                if (usePelotonSensors && pelotonSensorService.isConnected.value) {
                    updateMetricsWithDerivedValues(cadence, power, resistance)
                }
            }
        }
    }

    /**
     * Calculate derived metrics (speed, distance, total output, calories) from raw sensor data
     */
    private fun updateMetricsWithDerivedValues(cadence: Int, power: Int, resistance: Int) {
        val currentTimeMs = System.currentTimeMillis()

        // Calculate instantaneous speed from cadence (in mph)
        val speedMph = cadence * CADENCE_TO_SPEED_FACTOR

        // Only accumulate if ride is active and we have a valid previous timestamp
        if (isRideActive && lastUpdateTimeMs > 0) {
            val elapsedSeconds = (currentTimeMs - lastUpdateTimeMs) / 1000f

            // Only accumulate if elapsed time is reasonable (avoid jumps after pauses)
            if (elapsedSeconds > 0 && elapsedSeconds < 5) {
                // Accumulate distance: speed (mph) * time (hours)
                accumulatedDistanceMiles += speedMph * (elapsedSeconds / 3600f)

                // Accumulate energy output: power (watts) * time (seconds) = joules
                accumulatedOutputJoules += (power * elapsedSeconds).toLong()
            }
        }

        lastUpdateTimeMs = currentTimeMs

        // Calculate total output in kJ
        val totalOutputKJ = (accumulatedOutputJoules / 1000).toInt()

        // Estimate calories: roughly 1 kcal per kJ for cycling (accounting for ~25% efficiency)
        val calories = totalOutputKJ

        _metrics.value = RideMetrics(
            cadence = cadence,
            power = power,
            resistance = resistance,
            speed = speedMph,
            distance = accumulatedDistanceMiles,
            totalOutput = totalOutputKJ,
            calories = calories,
            heartRate = _metrics.value.heartRate,
            elapsedSeconds = _metrics.value.elapsedSeconds
        )
    }

    /**
     * Start tracking a new ride (begins accumulating metrics)
     */
    fun startRide() {
        isRideActive = true
        lastUpdateTimeMs = System.currentTimeMillis()
        Log.i(TAG, "Ride started - beginning metric accumulation")
    }

    /**
     * Pause the ride (stops accumulating but keeps values)
     */
    fun pauseRide() {
        isRideActive = false
        lastUpdateTimeMs = 0L
        Log.i(TAG, "Ride paused")
    }

    /**
     * Resume the ride after pause
     */
    fun resumeRide() {
        isRideActive = true
        lastUpdateTimeMs = System.currentTimeMillis()
        Log.i(TAG, "Ride resumed")
    }

    fun startScanning() {
        // First try to connect to Peloton's internal sensor service
        Log.i(TAG, "Attempting to connect to Peloton sensor service first...")
        usePelotonSensors = true
        _connectionState.value = BikeConnectionState(isScanning = true)
        pelotonSensorService.connect()

        // Also start Bluetooth scanning as fallback (will be used if Peloton service fails)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // Give Peloton service 2 seconds to connect
            if (!pelotonSensorService.isConnected.value) {
                Log.i(TAG, "Peloton service not available, falling back to Bluetooth")
                usePelotonSensors = false
                bikeService?.startScanning()
            }
        }
    }

    fun stopScanning() {
        bikeService?.stopScanning()
    }

    fun disconnect() {
        if (usePelotonSensors) {
            pelotonSensorService.disconnect()
            usePelotonSensors = false
        }
        bikeService?.disconnect()
        _connectionState.value = BikeConnectionState()
    }

    fun resetRideMetrics() {
        bikeService?.resetRideMetrics()
        // Reset accumulated values
        accumulatedOutputJoules = 0L
        accumulatedDistanceMiles = 0f
        lastUpdateTimeMs = 0L
        isRideActive = false
        _metrics.value = RideMetrics()
        Log.i(TAG, "Ride metrics reset")
    }

    override fun onCleared() {
        super.onCleared()
        if (usePelotonSensors) {
            pelotonSensorService.disconnect()
        }
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
    }
}
