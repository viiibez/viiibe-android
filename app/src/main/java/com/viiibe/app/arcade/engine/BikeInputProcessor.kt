package com.viiibe.app.arcade.engine

import com.viiibe.app.arcade.data.BikeAction
import com.viiibe.app.arcade.data.PlayerMetrics

/**
 * Processes raw bike metrics into game actions.
 * Detects resistance changes, power bursts, and normalizes cadence.
 */
class BikeInputProcessor {

    companion object {
        // Thresholds for detecting actions
        private const val RESISTANCE_CHANGE_THRESHOLD = 8  // Minimum change to register
        private const val POWER_BURST_THRESHOLD = 180      // Watts to trigger boost
        private const val POWER_BURST_DURATION_MS = 2000L  // Must sustain for this long

        // Smoothing
        private const val SMOOTHING_SAMPLES = 5
    }

    // History for smoothing and change detection
    private val cadenceHistory = ArrayDeque<Int>(SMOOTHING_SAMPLES)
    private val powerHistory = ArrayDeque<Int>(SMOOTHING_SAMPLES)
    private val resistanceHistory = ArrayDeque<Int>(SMOOTHING_SAMPLES)

    // Timestamps for detecting sustained actions
    private var powerBurstStartTime: Long? = null
    private var lastResistanceChangeTime = 0L
    private var lastResistance = 0

    // Current detected action
    var currentAction: BikeAction = BikeAction.None
        private set

    // Latest metrics (smoothed)
    var lastMetrics: PlayerMetrics = PlayerMetrics()
        private set

    /**
     * Process new metrics from the bike sensor.
     * Call this every frame or when new data arrives.
     * Note: Power is already converted to actual watts in PelotonSensorService.
     */
    fun processMetrics(metrics: PlayerMetrics) {
        val currentTime = System.currentTimeMillis()

        // Add to history (power is already in watts from PelotonSensorService)
        addToHistory(cadenceHistory, metrics.cadence)
        addToHistory(powerHistory, metrics.power)
        addToHistory(resistanceHistory, metrics.resistance)

        // Calculate smoothed values
        val smoothedCadence = cadenceHistory.average().toInt()
        val smoothedPower = powerHistory.average().toInt()
        val smoothedResistance = resistanceHistory.average().toInt()

        lastMetrics = PlayerMetrics(
            cadence = smoothedCadence,
            power = smoothedPower,
            resistance = smoothedResistance
        )

        // Detect actions
        currentAction = detectAction(metrics, currentTime)
    }

    private fun addToHistory(history: ArrayDeque<Int>, value: Int) {
        if (history.size >= SMOOTHING_SAMPLES) {
            history.removeFirst()
        }
        history.addLast(value)
    }

    private fun detectAction(metrics: PlayerMetrics, currentTime: Long): BikeAction {
        // Priority 1: Resistance changes (most immediate feedback needed)
        val resistanceAction = detectResistanceChange(metrics.resistance, currentTime)
        if (resistanceAction !is BikeAction.None) {
            return resistanceAction
        }

        // Priority 2: Power burst (sustained high power)
        // Power is already in actual watts from PelotonSensorService
        val powerBurstAction = detectPowerBurst(metrics.power, currentTime)
        if (powerBurstAction !is BikeAction.None) {
            return powerBurstAction
        }

        // Priority 3: Basic pedaling
        if (metrics.cadence > 30) {
            return BikeAction.Pedaling(metrics.cadence)
        }

        return BikeAction.None
    }

    private fun detectResistanceChange(currentResistance: Int, currentTime: Long): BikeAction {
        // Debounce resistance changes (300ms cooldown)
        if (currentTime - lastResistanceChangeTime < 300) {
            return BikeAction.None
        }

        val resistanceChange = currentResistance - lastResistance

        val action = when {
            resistanceChange >= RESISTANCE_CHANGE_THRESHOLD -> {
                lastResistanceChangeTime = currentTime
                BikeAction.ResistanceUp
            }
            resistanceChange <= -RESISTANCE_CHANGE_THRESHOLD -> {
                lastResistanceChangeTime = currentTime
                BikeAction.ResistanceDown
            }
            else -> BikeAction.None
        }

        lastResistance = currentResistance
        return action
    }

    private fun detectPowerBurst(currentPower: Int, currentTime: Long): BikeAction {
        if (currentPower >= POWER_BURST_THRESHOLD) {
            if (powerBurstStartTime == null) {
                powerBurstStartTime = currentTime
            } else if (currentTime - powerBurstStartTime!! >= POWER_BURST_DURATION_MS) {
                // Sustained high power - trigger burst
                return BikeAction.PowerBurst(currentPower)
            }
        } else {
            // Power dropped below threshold, reset
            powerBurstStartTime = null
        }

        return BikeAction.None
    }

    /**
     * Get the current cadence normalized to a 0-1 speed value.
     * 60 RPM = 0.5, 90 RPM = 0.75, 120 RPM = 1.0
     */
    fun getNormalizedSpeed(): Float {
        return (lastMetrics.cadence / 120f).coerceIn(0f, 1.5f)
    }

    /**
     * Check if the player is actively pedaling (cadence > threshold).
     */
    fun isPedaling(): Boolean {
        return lastMetrics.cadence > 30
    }

    /**
     * Reset all state (call when starting a new game).
     */
    fun reset() {
        cadenceHistory.clear()
        powerHistory.clear()
        resistanceHistory.clear()
        powerBurstStartTime = null
        lastResistanceChangeTime = 0L
        lastResistance = 0
        currentAction = BikeAction.None
        lastMetrics = PlayerMetrics()
    }
}
