package com.viiibe.app.arcade.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates procedural electronic beats that respond to playback speed.
 * Creates a dynamic music experience without requiring bundled audio files.
 */
class BeatGenerator {

    private var audioTrack: AudioTrack? = null
    private var generatorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentBpm = MutableStateFlow(120)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private var baseBpm = 120
    private var playbackSpeed = 1.0f

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // Different beat patterns for variety
    private val patterns = listOf(
        // Four on the floor
        listOf(1.0f, 0.0f, 0.5f, 0.0f, 1.0f, 0.0f, 0.5f, 0.0f),
        // Syncopated
        listOf(1.0f, 0.0f, 0.0f, 0.7f, 1.0f, 0.0f, 0.0f, 0.7f),
        // Driving
        listOf(1.0f, 0.3f, 0.6f, 0.3f, 1.0f, 0.3f, 0.6f, 0.3f),
        // Intense
        listOf(1.0f, 0.5f, 1.0f, 0.5f, 1.0f, 0.5f, 1.0f, 0.5f),
        // Building
        listOf(1.0f, 0.0f, 0.0f, 0.0f, 0.8f, 0.0f, 0.6f, 0.4f)
    )

    private var currentPatternIndex = 0

    fun initialize() {
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun setBaseBpm(bpm: Int) {
        baseBpm = bpm
        updateBpm()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2.0f)
        updateBpm()
    }

    private fun updateBpm() {
        _currentBpm.value = (baseBpm * playbackSpeed).toInt()
    }

    fun setPattern(index: Int) {
        currentPatternIndex = index.coerceIn(0, patterns.size - 1)
    }

    fun play() {
        if (_isPlaying.value) return

        _isPlaying.value = true
        audioTrack?.play()

        generatorJob = scope.launch {
            generateBeats()
        }
    }

    fun pause() {
        _isPlaying.value = false
        generatorJob?.cancel()
        audioTrack?.pause()
    }

    fun stop() {
        _isPlaying.value = false
        generatorJob?.cancel()
        audioTrack?.stop()
        audioTrack?.flush()
    }

    private suspend fun generateBeats() {
        val pattern = patterns[currentPatternIndex]
        var patternPosition = 0

        while (_isPlaying.value) {
            val currentBpm = _currentBpm.value
            val beatDurationMs = 60000.0 / currentBpm / 2 // Eighth notes
            val samplesPerBeat = (SAMPLE_RATE * beatDurationMs / 1000).toInt()

            val volume = pattern[patternPosition]
            if (volume > 0) {
                val samples = generateKickDrum(samplesPerBeat, volume)
                audioTrack?.write(samples, 0, samples.size)
            } else {
                // Silent beat
                val silence = ShortArray(samplesPerBeat)
                audioTrack?.write(silence, 0, silence.size)
            }

            patternPosition = (patternPosition + 1) % pattern.size

            // Small yield to allow speed updates
            yield()
        }
    }

    private fun generateKickDrum(samples: Int, volume: Float): ShortArray {
        val buffer = ShortArray(samples)
        val attackSamples = (samples * 0.05).toInt()
        val decaySamples = samples - attackSamples

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE

            // Frequency sweep from 150Hz down to 50Hz for kick drum sound
            val freqStart = 150.0
            val freqEnd = 50.0
            val freqDecay = (freqStart - freqEnd) * kotlin.math.exp(-t * 30) + freqEnd

            // Generate sine wave with frequency sweep
            val phase = 2 * PI * freqDecay * t
            var sample = sin(phase)

            // Add some harmonics for punch
            sample += 0.3 * sin(phase * 2)
            sample += 0.1 * sin(phase * 3)

            // Envelope
            val envelope = when {
                i < attackSamples -> i.toFloat() / attackSamples
                else -> kotlin.math.exp(-(i - attackSamples).toDouble() / (decaySamples * 0.3)).toFloat()
            }

            // Apply volume and envelope
            val finalSample = (sample * envelope * volume * 0.8 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            buffer[i] = finalSample.toShort()
        }

        return buffer
    }

    // Generate a snare-like sound
    private fun generateSnare(samples: Int, volume: Float): ShortArray {
        val buffer = ShortArray(samples)
        val random = java.util.Random()

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE

            // Noise component
            val noise = random.nextGaussian() * 0.5

            // Tonal component
            val tone = sin(2 * PI * 200 * t)

            // Envelope
            val envelope = kotlin.math.exp(-t * 20)

            val sample = ((noise * 0.7 + tone * 0.3) * envelope * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            buffer[i] = sample.toShort()
        }

        return buffer
    }

    // Generate hi-hat sound
    private fun generateHiHat(samples: Int, volume: Float): ShortArray {
        val buffer = ShortArray(samples)
        val random = java.util.Random()

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE

            // High-frequency noise
            val noise = random.nextGaussian()

            // Very fast decay
            val envelope = kotlin.math.exp(-t * 50)

            val sample = (noise * envelope * volume * 0.3 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            buffer[i] = sample.toShort()
        }

        return buffer
    }

    fun release() {
        stop()
        scope.cancel()
        audioTrack?.release()
        audioTrack = null
    }
}
