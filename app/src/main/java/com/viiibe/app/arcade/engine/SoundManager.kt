package com.viiibe.app.arcade.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import kotlinx.coroutines.*
import kotlin.math.sin

/**
 * Manages game sounds including beat generation for Rhythm Ride
 * and sound effects for all games.
 */
class SoundManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var beatJob: Job? = null
    private var isPlaying = false

    // Audio track for custom beat sounds
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            // ToneGenerator may not be available on all devices
        }
    }

    /**
     * Start playing a beat at the given BPM for Rhythm Ride
     */
    fun startBeat(bpm: Int, scope: CoroutineScope) {
        stopBeat()
        isPlaying = true

        val beatIntervalMs = (60_000L / bpm)

        beatJob = scope.launch(Dispatchers.Default) {
            var beatCount = 0
            while (isActive && isPlaying) {
                // Play different tones for downbeat vs upbeat
                val isDownbeat = beatCount % 4 == 0

                withContext(Dispatchers.Main) {
                    playBeatSound(isDownbeat)
                }

                beatCount++
                delay(beatIntervalMs)
            }
        }
    }

    /**
     * Stop the beat
     */
    fun stopBeat() {
        isPlaying = false
        beatJob?.cancel()
        beatJob = null
    }

    /**
     * Play a single beat sound
     */
    private fun playBeatSound(isDownbeat: Boolean) {
        try {
            // Use different tones for downbeat (accent) vs regular beat
            val toneType = if (isDownbeat) {
                ToneGenerator.TONE_PROP_BEEP  // Higher pitch for downbeat
            } else {
                ToneGenerator.TONE_PROP_ACK   // Lower pitch for regular beat
            }
            toneGenerator?.startTone(toneType, 50)  // 50ms duration
        } catch (e: Exception) {
            // Ignore tone generation errors
        }
    }

    /**
     * Play sound effect for game events
     */
    fun playEffect(effect: GameSoundEffect) {
        try {
            val toneType = when (effect) {
                GameSoundEffect.BOOST -> ToneGenerator.TONE_PROP_BEEP2
                GameSoundEffect.JUMP -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                GameSoundEffect.HIT_PERFECT -> ToneGenerator.TONE_PROP_ACK
                GameSoundEffect.HIT_GOOD -> ToneGenerator.TONE_PROP_PROMPT
                GameSoundEffect.MISS -> ToneGenerator.TONE_PROP_NACK
                GameSoundEffect.CRASH -> ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE
                GameSoundEffect.WIN -> ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE
                GameSoundEffect.LOSE -> ToneGenerator.TONE_CDMA_CALLDROP_LITE
                GameSoundEffect.COUNTDOWN -> ToneGenerator.TONE_PROP_BEEP
                GameSoundEffect.GO -> ToneGenerator.TONE_PROP_BEEP2
                GameSoundEffect.ZOMBIE_GROAN -> ToneGenerator.TONE_CDMA_LOW_L
                GameSoundEffect.ZOMBIE_CLOSE -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                GameSoundEffect.HEARTBEAT -> ToneGenerator.TONE_CDMA_LOW_PBX_L
                GameSoundEffect.COMBO -> ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE
            }
            toneGenerator?.startTone(toneType, 150)
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Generate and play a synthesized drum beat pattern
     */
    fun playDrumBeat(bpm: Int, scope: CoroutineScope) {
        stopBeat()
        isPlaying = true

        val beatIntervalMs = (60_000L / bpm)

        beatJob = scope.launch(Dispatchers.Default) {
            // Pre-generate beat sounds
            val kickSound = generateKickDrum()
            val hihatSound = generateHiHat()

            var beatCount = 0

            while (isActive && isPlaying) {
                // Simple 4/4 beat pattern: kick on 1 and 3, hihat on all beats
                val isKick = beatCount % 2 == 0

                try {
                    if (isKick) {
                        playPcmSound(kickSound)
                    } else {
                        playPcmSound(hihatSound)
                    }
                } catch (e: Exception) {
                    // Fallback to tone generator
                    withContext(Dispatchers.Main) {
                        playBeatSound(isKick)
                    }
                }

                beatCount++
                delay(beatIntervalMs)
            }
        }
    }

    private fun generateKickDrum(): ShortArray {
        val duration = 0.1  // 100ms
        val numSamples = (sampleRate * duration).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Kick drum: low frequency sine wave with quick decay
            val frequency = 60.0 * (1.0 - t * 5).coerceAtLeast(0.3)  // Pitch drops
            val envelope = (1.0 - t * 10).coerceAtLeast(0.0)  // Quick decay
            val sample = sin(2.0 * Math.PI * frequency * t) * envelope
            samples[i] = (sample * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return samples
    }

    private fun generateHiHat(): ShortArray {
        val duration = 0.05  // 50ms
        val numSamples = (sampleRate * duration).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Hi-hat: noise with quick decay
            val noise = (Math.random() * 2 - 1)
            val envelope = (1.0 - t * 20).coerceAtLeast(0.0)
            val sample = noise * envelope
            samples[i] = (sample * Short.MAX_VALUE * 0.3).toInt().toShort()
        }
        return samples
    }

    private fun playPcmSound(samples: ShortArray) {
        val bufferSize = samples.size * 2  // 2 bytes per short

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()

        // Release after playing
        Thread {
            Thread.sleep(200)
            audioTrack.release()
        }.start()
    }

    fun release() {
        stopBeat()
        toneGenerator?.release()
        toneGenerator = null
        audioTrack?.release()
        audioTrack = null
    }
}

enum class GameSoundEffect {
    BOOST,
    JUMP,
    HIT_PERFECT,
    HIT_GOOD,
    MISS,
    CRASH,
    WIN,
    LOSE,
    COUNTDOWN,
    GO,
    ZOMBIE_GROAN,
    ZOMBIE_CLOSE,
    HEARTBEAT,
    COMBO
}
