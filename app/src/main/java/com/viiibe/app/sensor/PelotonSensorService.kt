package com.viiibe.app.sensor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service that connects to Peloton's AffernetService to get
 * real-time cadence, power, and resistance data from the bike.
 *
 * Uses callback registration (IV1Interface) to receive sensor updates.
 */
class PelotonSensorService(private val context: Context) {

    companion object {
        private const val TAG = "PelotonSensorService"

        // AffernetService interfaces
        private const val V1_INTERFACE = "com.onepeloton.affernetservice.IV1Interface"
        private const val V1_CALLBACK_INTERFACE = "com.onepeloton.affernetservice.IV1Callback"
        private const val AFFERNET_PACKAGE = "com.onepeloton.affernetservice"

        // Transaction codes for IV1Interface
        private const val TRANSACTION_REGISTER_CALLBACK = 1
        private const val TRANSACTION_UNREGISTER_CALLBACK = 2
    }

    // Sensor data flows
    private val _cadence = MutableStateFlow(0)
    val cadence: StateFlow<Int> = _cadence.asStateFlow()

    private val _power = MutableStateFlow(0)
    val power: StateFlow<Int> = _power.asStateFlow()

    private val _resistance = MutableStateFlow(0)
    val resistance: StateFlow<Int> = _resistance.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var serviceBinder: IBinder? = null
    private var callbackBinder: IBinder? = null
    private var isCallbackRegistered = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.w(TAG, "Peloton service connected: $name, binder: $binder")
            if (binder != null) {
                serviceBinder = binder
                _isConnected.value = true
                _error.value = null

                try {
                    Log.w(TAG, "Binder interface descriptor: ${binder.interfaceDescriptor}")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not get interface descriptor: ${e.message}")
                }

                // Register callback to receive sensor data
                registerCallback(binder)
            } else {
                _error.value = "Failed to get sensor service binder"
                _isConnected.value = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Peloton sensor service disconnected: $name")
            isCallbackRegistered = false
            serviceBinder = null
            _isConnected.value = false
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "Peloton sensor service binding died: $name")
            isCallbackRegistered = false
            serviceBinder = null
            _isConnected.value = false
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "Peloton sensor service null binding: $name")
            _error.value = "Peloton sensor service not available"
            _isConnected.value = false
        }
    }

    /**
     * Connect to Peloton's AffernetService
     */
    fun connect() {
        Log.w(TAG, "Attempting to connect to AffernetService (IV1Interface)...")

        try {
            val intent = Intent(V1_INTERFACE).apply {
                setPackage(AFFERNET_PACKAGE)
            }
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            if (bound) {
                Log.w(TAG, "Successfully initiated bind with IV1Interface!")
            } else {
                Log.e(TAG, "Failed to bind to AffernetService")
                _error.value = "Could not connect to Peloton bike sensors"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to AffernetService", e)
            _error.value = "Error connecting: ${e.message}"
        }
    }

    /**
     * Create the callback binder that receives sensor updates
     */
    private fun createCallbackBinder(): IBinder {
        return object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                Log.w(TAG, "Callback onTransact called with code: $code")

                return when (code) {
                    1 -> { // onSensorDataChange
                        try {
                            data.enforceInterface(V1_CALLBACK_INTERFACE)
                            Log.w(TAG, "Callback interface enforced successfully")

                            val hasData = data.readInt()
                            Log.w(TAG, "Has data flag: $hasData")

                            if (hasData != 0) {
                                // Parse BikeData from the parcel
                                parseBikeDataFromCallback(data)
                            } else {
                                Log.w(TAG, "No bike data in callback")
                            }
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing sensor data callback", e)
                            false
                        }
                    }
                    2 -> { // onSensorError
                        try {
                            data.enforceInterface(V1_CALLBACK_INTERFACE)
                            val errorCode = data.readLong()
                            Log.w(TAG, "Sensor error callback: $errorCode")
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing sensor error callback", e)
                            false
                        }
                    }
                    3 -> { // onCalibrationStatus
                        try {
                            data.enforceInterface(V1_CALLBACK_INTERFACE)
                            val status = data.readInt()
                            val success = data.readInt() != 0
                            val errorCode = data.readLong()
                            Log.w(TAG, "Calibration status: status=$status success=$success error=$errorCode")
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing calibration status", e)
                            false
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown callback transaction code: $code")
                        super.onTransact(code, data, reply, flags)
                    }
                }
            }
        }
    }

    /**
     * Parse BikeData from a callback parcel
     * Based on BikeData.java - the fields are in this EXACT order:
     * 1. mRPM (long)
     * 2. mPower (long)
     * 3. mStepperMotorPosition (long)
     * 4. mLoadCellReading (long)
     * 5. mCurrentResistance (int)
     * 6. mTargetResistance (int)
     * ... and many more fields after
     */
    private fun parseBikeDataFromCallback(data: Parcel) {
        try {
            // Read fields in EXACT order from BikeData.java readFromParcel()
            val rpm = data.readLong()
            val power = data.readLong()
            val stepperMotorPosition = data.readLong()
            val loadCellReading = data.readLong()
            val currentResistance = data.readInt()
            val targetResistance = data.readInt()

            // Read additional fields to help debug
            val int3 = if (data.dataAvail() >= 4) data.readInt() else -999
            val int4 = if (data.dataAvail() >= 4) data.readInt() else -999
            val int5 = if (data.dataAvail() >= 4) data.readInt() else -999

            // Update the sensor values
            // Power needs to be divided by 10 to get watts (raw value is in deciwatts)
            _cadence.value = rpm.toInt()
            _power.value = (power / 10).toInt()

            // Resistance: Peloton Bike+ may report 0-100 but the stepper position gives actual range
            // If targetResistance maxes out at 100 but knob still turns, use stepper position
            // Stepper motor position typically goes from ~0 to ~2000+ for full range
            val resistanceFromStepper = if (stepperMotorPosition > 0) {
                // Map stepper position (typically 0-2000) to 0-100 percentage
                ((stepperMotorPosition.toFloat() / 2000f) * 100f).coerceIn(0f, 100f).toInt()
            } else {
                targetResistance
            }

            // Use the more accurate value
            _resistance.value = if (stepperMotorPosition > 100) resistanceFromStepper else targetResistance

            Log.w(TAG, "BikeData: RPM=$rpm, Power=${power/10}W, TargetRes=$targetResistance, StepperPos=$stepperMotorPosition, CalcRes=$resistanceFromStepper")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing BikeData: ${e.message}")

            // If parsing fails, try to read raw data for debugging
            try {
                val startPos = data.dataPosition()

                // Read as longs (first 4 values)
                val long1 = if (data.dataAvail() >= 8) data.readLong() else -1
                val long2 = if (data.dataAvail() >= 8) data.readLong() else -1
                val long3 = if (data.dataAvail() >= 8) data.readLong() else -1
                val long4 = if (data.dataAvail() >= 8) data.readLong() else -1
                val int1 = if (data.dataAvail() >= 4) data.readInt() else -1
                val int2 = if (data.dataAvail() >= 4) data.readInt() else -1

                Log.w(TAG, "Raw data - L1=$long1, L2=$long2, L3=$long3, L4=$long4, I1=$int1, I2=$int2")

                // Try to use these values
                if (long1 > 0 && long1 < 1000) _cadence.value = long1.toInt()
                if (long2 > 0 && long2 < 10000) _power.value = long2.toInt()
                if (int1 > 0 && int1 < 200) _resistance.value = int1
            } catch (e2: Exception) {
                Log.e(TAG, "Error reading raw data: ${e2.message}")
            }
        }
    }

    /**
     * Register our callback with the service
     */
    private fun registerCallback(binder: IBinder) {
        Log.w(TAG, "Registering callback with AffernetService...")

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            callbackBinder = createCallbackBinder()

            data.writeInterfaceToken(V1_INTERFACE)
            data.writeStrongBinder(callbackBinder)
            data.writeString("FreeSpin") // Client identifier

            val success = binder.transact(TRANSACTION_REGISTER_CALLBACK, data, reply, 0)
            if (success) {
                reply.readException()
                isCallbackRegistered = true
                Log.w(TAG, "Successfully registered callback! Waiting for sensor data...")
            } else {
                Log.e(TAG, "Failed to register callback")
                _error.value = "Failed to register for sensor updates"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering callback", e)
            _error.value = "Error registering callback: ${e.message}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Unregister our callback from the service
     */
    private fun unregisterCallback() {
        val binder = serviceBinder ?: return
        if (!isCallbackRegistered || callbackBinder == null) return

        Log.w(TAG, "Unregistering callback from AffernetService...")

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(V1_INTERFACE)
            data.writeStrongBinder(callbackBinder)
            data.writeString("FreeSpin")

            val success = binder.transact(TRANSACTION_UNREGISTER_CALLBACK, data, reply, 0)
            if (success) {
                reply.readException()
                Log.w(TAG, "Successfully unregistered callback")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering callback", e)
        } finally {
            data.recycle()
            reply.recycle()
            isCallbackRegistered = false
        }
    }

    /**
     * Disconnect from the sensor service
     */
    fun disconnect() {
        Log.w(TAG, "Disconnecting from Peloton sensor service")

        unregisterCallback()

        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding service", e)
        }

        serviceBinder = null
        callbackBinder = null
        _isConnected.value = false
        _cadence.value = 0
        _power.value = 0
        _resistance.value = 0
    }
}
