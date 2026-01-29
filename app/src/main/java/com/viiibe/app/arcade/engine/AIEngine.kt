package com.viiibe.app.arcade.engine

import com.viiibe.app.arcade.data.AIDifficulty
import com.viiibe.app.arcade.data.AIPersonality
import com.viiibe.app.arcade.data.AIPlayer
import com.viiibe.app.arcade.data.PlayerMetrics
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates realistic AI opponent metrics based on difficulty and personality.
 */
class AIEngine {

    companion object {
        // Fatigue simulation
        private const val FATIGUE_ONSET_MS = 30_000L  // Start fatiguing after 30s
        private const val MAX_FATIGUE_REDUCTION = 0.3f  // Max 30% reduction from fatigue
    }

    // Track AI state for each player
    private val aiStates = mutableMapOf<Int, AIState>()

    private data class AIState(
        var targetCadence: Int = 70,
        var targetPower: Int = 150,
        var currentCadence: Float = 70f,
        var currentPower: Float = 150f,
        var currentResistance: Int = 50,
        var fatigueLevel: Float = 0f,
        var lastBurstTime: Long = 0,
        var isRecovering: Boolean = false,
        var recoveryEndTime: Long = 0
    )

    /**
     * Generate metrics for an AI player.
     *
     * @param player The AI player configuration
     * @param elapsedTimeMs Time since game start
     * @param playerPosition Optional player position (for adaptive AI)
     */
    fun generateMetrics(
        player: AIPlayer,
        elapsedTimeMs: Long,
        playerPosition: Float = 0f
    ): PlayerMetrics {
        // Get or create AI state
        val state = aiStates.getOrPut(player.id) {
            AIState(
                targetCadence = player.difficulty.cadenceRange.random(),
                targetPower = player.difficulty.powerRange.random()
            )
        }

        // Update targets based on personality and game state
        updateTargets(player, state, elapsedTimeMs, playerPosition)

        // Apply difficulty consistency (how closely AI tracks targets)
        val consistency = player.difficulty.consistency

        // Smooth transition to targets
        state.currentCadence = lerp(state.currentCadence, state.targetCadence.toFloat(), 0.1f * consistency)
        state.currentPower = lerp(state.currentPower, state.targetPower.toFloat(), 0.1f * consistency)

        // Add noise based on difficulty (easier = more noise)
        val noise = (1f - consistency) * 10f
        val finalCadence = (state.currentCadence + Random.nextFloat() * noise - noise / 2).toInt()
        val finalPower = (state.currentPower + Random.nextFloat() * noise * 2 - noise).toInt()

        // Apply fatigue
        val fatigueMultiplier = 1f - state.fatigueLevel * MAX_FATIGUE_REDUCTION
        val fatiguedPower = (finalPower * fatigueMultiplier).toInt()

        return PlayerMetrics(
            cadence = finalCadence.coerceIn(0, 150),
            power = fatiguedPower.coerceIn(0, 500),
            resistance = state.currentResistance
        )
    }

    private fun updateTargets(
        player: AIPlayer,
        state: AIState,
        elapsedTimeMs: Long,
        playerPosition: Float
    ) {
        val difficulty = player.difficulty
        val personality = player.personality

        // Base targets from difficulty
        var baseCadence = difficulty.cadenceRange.average().toInt()
        var basePower = difficulty.powerRange.average().toInt()

        // Apply personality modifiers
        when (personality) {
            AIPersonality.STEADY -> {
                // Very consistent, minimal variation
                state.targetCadence = baseCadence
                state.targetPower = basePower
            }

            AIPersonality.SPRINTER -> {
                // Periodic bursts followed by recovery
                val cycleMs = 15_000L  // 15 second cycles
                val phase = (elapsedTimeMs % cycleMs) / cycleMs.toFloat()

                if (phase < 0.3f && !state.isRecovering) {
                    // Sprint phase
                    state.targetCadence = difficulty.cadenceRange.last + 10
                    state.targetPower = difficulty.powerRange.last + 30
                    state.lastBurstTime = elapsedTimeMs
                } else {
                    // Recovery phase
                    state.isRecovering = phase >= 0.3f && phase < 0.6f
                    if (state.isRecovering) {
                        state.targetCadence = difficulty.cadenceRange.first - 10
                        state.targetPower = difficulty.powerRange.first
                    } else {
                        state.targetCadence = baseCadence
                        state.targetPower = basePower
                        state.isRecovering = false
                    }
                }
            }

            AIPersonality.CLIMBER -> {
                // Higher resistance, lower cadence, consistent power
                state.targetCadence = (baseCadence * 0.85f).toInt()
                state.targetPower = (basePower * 1.1f).toInt()
                state.currentResistance = 70
            }

            AIPersonality.BALANCED -> {
                // Adapts to player position
                if (playerPosition > 0) {
                    // If player is ahead, push harder
                    val catchUpFactor = 1f + (playerPosition * 0.2f).coerceAtMost(0.3f)
                    state.targetCadence = (baseCadence * catchUpFactor).toInt()
                    state.targetPower = (basePower * catchUpFactor).toInt()
                } else {
                    state.targetCadence = baseCadence
                    state.targetPower = basePower
                }
            }

            AIPersonality.AGGRESSIVE -> {
                // Always pushes hard, fatigues faster
                state.targetCadence = difficulty.cadenceRange.last
                state.targetPower = difficulty.powerRange.last

                // Increase fatigue faster for aggressive personality
                if (elapsedTimeMs > FATIGUE_ONSET_MS) {
                    val fatigueRate = 0.00002f  // Faster fatigue
                    state.fatigueLevel = ((elapsedTimeMs - FATIGUE_ONSET_MS) * fatigueRate).coerceAtMost(0.5f)
                }
            }
        }

        // Add time-based variation (simulates natural human variation)
        val timeVariation = sin(elapsedTimeMs / 3000.0).toFloat() * 5f
        state.targetCadence = (state.targetCadence + timeVariation).toInt()

        // Apply general fatigue (all personalities except noted above)
        if (personality != AIPersonality.AGGRESSIVE && elapsedTimeMs > FATIGUE_ONSET_MS) {
            val fatigueRate = 0.00001f
            state.fatigueLevel = ((elapsedTimeMs - FATIGUE_ONSET_MS) * fatigueRate).coerceAtMost(0.3f)
        }

        // Ensure targets stay within difficulty bounds (with some allowance for personality)
        state.targetCadence = state.targetCadence.coerceIn(
            difficulty.cadenceRange.first - 15,
            difficulty.cadenceRange.last + 15
        )
        state.targetPower = state.targetPower.coerceIn(
            difficulty.powerRange.first - 20,
            difficulty.powerRange.last + 40
        )
    }

    /**
     * Reset AI state for a new game.
     */
    fun reset() {
        aiStates.clear()
    }

    /**
     * Reset state for a specific AI player.
     */
    fun resetPlayer(playerId: Int) {
        aiStates.remove(playerId)
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
