package com.viiibe.app.arcade.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * MIDI-based synthesizer that generates musical patterns with multiple instruments.
 * Uses FM synthesis and sample-based techniques for realistic instrument sounds.
 */
class MidiSynthesizer {

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentBpm = MutableStateFlow(120)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private var baseBpm = 120
    private var playbackSpeed = 1.0f

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // MIDI note numbers
        private const val C3 = 48
        private const val D3 = 50
        private const val E3 = 52
        private const val F3 = 53
        private const val G3 = 55
        private const val A3 = 57
        private const val B3 = 59
        private const val C4 = 60
        private const val D4 = 62
        private const val E4 = 64
        private const val G4 = 67
        private const val C5 = 72
    }

    // MIDI instruments
    enum class Instrument {
        BASS,
        SYNTH_LEAD,
        PAD,
        DRUMS_KICK,
        DRUMS_SNARE,
        DRUMS_HIHAT,
        DRUMS_CLAP
    }

    // MIDI note event
    data class MidiNote(
        val instrument: Instrument,
        val note: Int,           // MIDI note number (0-127)
        val velocity: Float,     // 0.0 to 1.0
        val startBeat: Float,    // Beat position in pattern
        val duration: Float      // Duration in beats
    )

    // Different musical patterns (16 beats = 4 bars of 4/4)
    private val patterns = listOf(
        // Pattern 0: Four on the floor dance
        createDancePattern(),
        // Pattern 1: Funky syncopated
        createFunkyPattern(),
        // Pattern 2: Driving rock
        createDrivingPattern(),
        // Pattern 3: Intense EDM
        createIntensePattern(),
        // Pattern 4: Chill groove
        createChillPattern()
    )

    private var currentPatternIndex = 0
    private var currentPattern = patterns[0]

    private fun createDancePattern(): List<MidiNote> = buildList {
        // Kick on every beat
        for (i in 0 until 16) {
            add(MidiNote(Instrument.DRUMS_KICK, C3, 1.0f, i.toFloat(), 0.25f))
        }
        // Snare on 2 and 4
        for (i in listOf(1, 3, 5, 7, 9, 11, 13, 15)) {
            add(MidiNote(Instrument.DRUMS_SNARE, D3, 0.9f, i.toFloat(), 0.25f))
        }
        // Hi-hats on off-beats
        for (i in 0 until 32) {
            add(MidiNote(Instrument.DRUMS_HIHAT, F3, 0.5f, i * 0.5f, 0.1f))
        }
        // Bass line
        val bassNotes = listOf(C3, C3, G3, G3, A3, A3, F3, F3)
        bassNotes.forEachIndexed { idx, note ->
            add(MidiNote(Instrument.BASS, note, 0.8f, idx * 2f, 1.5f))
        }
        // Synth chords
        add(MidiNote(Instrument.PAD, C4, 0.4f, 0f, 4f))
        add(MidiNote(Instrument.PAD, E4, 0.4f, 0f, 4f))
        add(MidiNote(Instrument.PAD, G4, 0.4f, 0f, 4f))
        add(MidiNote(Instrument.PAD, A3, 0.4f, 4f, 4f))
        add(MidiNote(Instrument.PAD, C4, 0.4f, 4f, 4f))
        add(MidiNote(Instrument.PAD, E4, 0.4f, 4f, 4f))
    }

    private fun createFunkyPattern(): List<MidiNote> = buildList {
        // Syncopated kick
        for (beat in listOf(0f, 0.75f, 2f, 3.5f, 4f, 4.75f, 6f, 7.5f, 8f, 8.75f, 10f, 11.5f, 12f, 12.75f, 14f, 15.5f)) {
            add(MidiNote(Instrument.DRUMS_KICK, C3, 1.0f, beat, 0.25f))
        }
        // Claps
        for (i in listOf(1, 3, 5, 7, 9, 11, 13, 15)) {
            add(MidiNote(Instrument.DRUMS_CLAP, E3, 0.85f, i.toFloat(), 0.25f))
        }
        // Funky hi-hats
        for (i in 0 until 64) {
            val vel = if (i % 4 == 0) 0.7f else 0.3f
            add(MidiNote(Instrument.DRUMS_HIHAT, F3, vel, i * 0.25f, 0.1f))
        }
        // Funky bass
        val bassPattern = listOf(
            C3 to 0f, C3 to 0.5f, C3 to 1.25f, G3 to 2f, G3 to 3f,
            F3 to 4f, F3 to 4.5f, F3 to 5.25f, G3 to 6f, A3 to 7f
        )
        bassPattern.forEach { (note, beat) ->
            add(MidiNote(Instrument.BASS, note, 0.9f, beat, 0.4f))
            add(MidiNote(Instrument.BASS, note, 0.9f, beat + 8f, 0.4f))
        }
        // Synth stabs
        add(MidiNote(Instrument.SYNTH_LEAD, E4, 0.7f, 1.5f, 0.25f))
        add(MidiNote(Instrument.SYNTH_LEAD, D4, 0.7f, 5.5f, 0.25f))
        add(MidiNote(Instrument.SYNTH_LEAD, E4, 0.7f, 9.5f, 0.25f))
        add(MidiNote(Instrument.SYNTH_LEAD, G4, 0.7f, 13.5f, 0.25f))
    }

    private fun createDrivingPattern(): List<MidiNote> = buildList {
        // Driving kick
        for (i in 0 until 32) {
            add(MidiNote(Instrument.DRUMS_KICK, C3, 0.9f, i * 0.5f, 0.2f))
        }
        // Snare on 2 and 4
        for (i in listOf(1, 3, 5, 7, 9, 11, 13, 15)) {
            add(MidiNote(Instrument.DRUMS_SNARE, D3, 1.0f, i.toFloat(), 0.25f))
        }
        // Crash hi-hats
        for (i in 0 until 16) {
            add(MidiNote(Instrument.DRUMS_HIHAT, F3, 0.6f, i.toFloat(), 0.15f))
        }
        // Power bass
        add(MidiNote(Instrument.BASS, C3, 1.0f, 0f, 3.5f))
        add(MidiNote(Instrument.BASS, G3, 1.0f, 4f, 3.5f))
        add(MidiNote(Instrument.BASS, A3, 1.0f, 8f, 3.5f))
        add(MidiNote(Instrument.BASS, F3, 1.0f, 12f, 3.5f))
        // Synth power chords
        add(MidiNote(Instrument.SYNTH_LEAD, C4, 0.6f, 0f, 4f))
        add(MidiNote(Instrument.SYNTH_LEAD, G4, 0.6f, 0f, 4f))
        add(MidiNote(Instrument.SYNTH_LEAD, G3, 0.6f, 4f, 4f))
        add(MidiNote(Instrument.SYNTH_LEAD, D4, 0.6f, 4f, 4f))
    }

    private fun createIntensePattern(): List<MidiNote> = buildList {
        // Double-time kick
        for (i in 0 until 64) {
            add(MidiNote(Instrument.DRUMS_KICK, C3, 0.95f, i * 0.25f, 0.1f))
        }
        // Snare rolls
        for (i in 0 until 32) {
            if (i % 2 == 1) {
                add(MidiNote(Instrument.DRUMS_SNARE, D3, 0.9f, i * 0.5f, 0.15f))
            }
        }
        // Rapid hi-hats
        for (i in 0 until 128) {
            add(MidiNote(Instrument.DRUMS_HIHAT, F3, 0.4f, i * 0.125f, 0.05f))
        }
        // Aggressive bass
        val bassNotes = listOf(C3, C3, D3, E3, C3, C3, G3, F3)
        bassNotes.forEachIndexed { idx, note ->
            add(MidiNote(Instrument.BASS, note, 1.0f, idx * 2f, 1.8f))
        }
        // Screaming synth
        add(MidiNote(Instrument.SYNTH_LEAD, C5, 0.8f, 0f, 2f))
        add(MidiNote(Instrument.SYNTH_LEAD, D4, 0.8f, 2f, 2f))
        add(MidiNote(Instrument.SYNTH_LEAD, E4, 0.8f, 4f, 2f))
        add(MidiNote(Instrument.SYNTH_LEAD, G4, 0.8f, 6f, 2f))
        add(MidiNote(Instrument.SYNTH_LEAD, C5, 0.8f, 8f, 2f))
        add(MidiNote(Instrument.SYNTH_LEAD, E4, 0.8f, 10f, 2f))
        add(MidiNote(Instrument.SYNTH_LEAD, D4, 0.8f, 12f, 4f))
    }

    private fun createChillPattern(): List<MidiNote> = buildList {
        // Sparse kick
        for (i in listOf(0, 3, 4, 7, 8, 11, 12, 15)) {
            add(MidiNote(Instrument.DRUMS_KICK, C3, 0.7f, i.toFloat(), 0.3f))
        }
        // Rimshot snare
        for (i in listOf(2, 6, 10, 14)) {
            add(MidiNote(Instrument.DRUMS_SNARE, D3, 0.5f, i.toFloat(), 0.2f))
        }
        // Soft hi-hats
        for (i in 0 until 32) {
            add(MidiNote(Instrument.DRUMS_HIHAT, F3, 0.25f, i * 0.5f, 0.15f))
        }
        // Smooth bass
        add(MidiNote(Instrument.BASS, C3, 0.6f, 0f, 3.5f))
        add(MidiNote(Instrument.BASS, E3, 0.6f, 4f, 3.5f))
        add(MidiNote(Instrument.BASS, A3, 0.6f, 8f, 3.5f))
        add(MidiNote(Instrument.BASS, G3, 0.6f, 12f, 3.5f))
        // Ambient pad
        add(MidiNote(Instrument.PAD, C4, 0.3f, 0f, 8f))
        add(MidiNote(Instrument.PAD, E4, 0.3f, 0f, 8f))
        add(MidiNote(Instrument.PAD, G4, 0.3f, 0f, 8f))
        add(MidiNote(Instrument.PAD, A3, 0.3f, 8f, 8f))
        add(MidiNote(Instrument.PAD, C4, 0.3f, 8f, 8f))
        add(MidiNote(Instrument.PAD, E4, 0.3f, 8f, 8f))
    }

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
        currentPattern = patterns[currentPatternIndex]
    }

    fun play() {
        if (_isPlaying.value) return

        _isPlaying.value = true
        audioTrack?.play()

        synthJob = scope.launch {
            synthesizePattern()
        }
    }

    fun pause() {
        _isPlaying.value = false
        synthJob?.cancel()
        audioTrack?.pause()
    }

    fun stop() {
        _isPlaying.value = false
        synthJob?.cancel()
        audioTrack?.stop()
        audioTrack?.flush()
    }

    private suspend fun synthesizePattern() {
        val patternLengthBeats = 16f

        while (_isPlaying.value) {
            val currentBpm = _currentBpm.value
            val beatsPerSecond = currentBpm / 60f
            val patternDurationMs = (patternLengthBeats / beatsPerSecond * 1000).toLong()
            val samplesPerPattern = (SAMPLE_RATE * patternLengthBeats / beatsPerSecond).toInt()

            // Pre-render the pattern
            val buffer = ShortArray(samplesPerPattern * 2) // Stereo

            // Render each note
            for (note in currentPattern) {
                renderNote(buffer, note, beatsPerSecond)
            }

            // Normalize and apply limiter
            applyLimiter(buffer)

            // Write in chunks
            val chunkSize = 4096
            var offset = 0
            while (offset < buffer.size && _isPlaying.value) {
                val remaining = buffer.size - offset
                val writeSize = minOf(chunkSize, remaining)
                audioTrack?.write(buffer, offset, writeSize)
                offset += writeSize
                yield()
            }
        }
    }

    private fun renderNote(buffer: ShortArray, note: MidiNote, beatsPerSecond: Float) {
        val startSample = (note.startBeat / beatsPerSecond * SAMPLE_RATE).toInt() * 2
        val durationSamples = (note.duration / beatsPerSecond * SAMPLE_RATE).toInt()
        val frequency = midiNoteToFrequency(note.note)

        for (i in 0 until durationSamples) {
            val sampleIndex = startSample + i * 2
            if (sampleIndex >= buffer.size - 1) break

            val t = i.toDouble() / SAMPLE_RATE
            val sample = synthesizeInstrument(note.instrument, frequency, t, durationSamples, i, note.velocity)

            // Mix into buffer (stereo)
            val currentL = buffer[sampleIndex].toInt()
            val currentR = buffer[sampleIndex + 1].toInt()
            buffer[sampleIndex] = (currentL + sample.first).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer[sampleIndex + 1] = (currentR + sample.second).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthesizeInstrument(instrument: Instrument, freq: Double, t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Pair<Int, Int> {
        val sample = when (instrument) {
            Instrument.BASS -> synthesizeBass(freq, t, totalSamples, currentSample, velocity)
            Instrument.SYNTH_LEAD -> synthesizeSynthLead(freq, t, totalSamples, currentSample, velocity)
            Instrument.PAD -> synthesizePad(freq, t, totalSamples, currentSample, velocity)
            Instrument.DRUMS_KICK -> synthesizeKick(t, totalSamples, currentSample, velocity)
            Instrument.DRUMS_SNARE -> synthesizeSnare(t, totalSamples, currentSample, velocity)
            Instrument.DRUMS_HIHAT -> synthesizeHiHat(t, totalSamples, currentSample, velocity)
            Instrument.DRUMS_CLAP -> synthesizeClap(t, totalSamples, currentSample, velocity)
        }

        val intSample = (sample * Short.MAX_VALUE * 0.3).toInt()
        return Pair(intSample, intSample)
    }

    private fun synthesizeBass(freq: Double, t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        // Subtractive bass with filter
        val env = adsr(currentSample, totalSamples, 0.01, 0.1, 0.7, 0.3)
        val filterEnv = adsr(currentSample, totalSamples, 0.01, 0.2, 0.3, 0.2)

        // Sawtooth with harmonics
        var sample = 0.0
        for (h in 1..8) {
            val amplitude = 1.0 / h * filterEnv.pow(h * 0.5)
            sample += sin(2 * PI * freq * h * t) * amplitude
        }

        return sample * env * velocity * 0.8
    }

    private fun synthesizeSynthLead(freq: Double, t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        // FM synthesis lead
        val env = adsr(currentSample, totalSamples, 0.02, 0.1, 0.8, 0.4)

        // Carrier with FM modulation
        val modIndex = 3.0 * env
        val modFreq = freq * 2
        val modulator = sin(2 * PI * modFreq * t) * modIndex
        val carrier = sin(2 * PI * freq * t + modulator)

        // Add some detuned oscillators for thickness
        val detune1 = sin(2 * PI * freq * 1.005 * t + modulator * 0.5)
        val detune2 = sin(2 * PI * freq * 0.995 * t + modulator * 0.5)

        return (carrier * 0.5 + detune1 * 0.25 + detune2 * 0.25) * env * velocity * 0.7
    }

    private fun synthesizePad(freq: Double, t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        // Warm pad with slow attack
        val env = adsr(currentSample, totalSamples, 0.3, 0.2, 0.9, 0.5)

        // Multiple detuned oscillators
        var sample = 0.0
        val detuneAmounts = listOf(0.0, 0.003, -0.003, 0.007, -0.007)
        for (detune in detuneAmounts) {
            sample += sin(2 * PI * freq * (1.0 + detune) * t) * 0.2
        }

        // Add sub oscillator
        sample += sin(2 * PI * freq * 0.5 * t) * 0.15

        return sample * env * velocity * 0.5
    }

    private fun synthesizeKick(t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        val env = exp(-t * 15)
        val pitchEnv = exp(-t * 40)

        // Pitch drops from 150Hz to 50Hz
        val freq = 50.0 + 100.0 * pitchEnv

        // Main body
        val body = sin(2 * PI * freq * t)
        // Click
        val click = sin(2 * PI * 1000 * t) * exp(-t * 100)

        return (body * 0.9 + click * 0.1) * env * velocity
    }

    private fun synthesizeSnare(t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        val env = exp(-t * 20)
        val noiseEnv = exp(-t * 15)

        // Tonal component
        val tone = sin(2 * PI * 200 * t) * exp(-t * 30)

        // Noise component
        val noise = (Math.random() * 2 - 1) * noiseEnv

        return (tone * 0.4 + noise * 0.6) * env * velocity * 0.8
    }

    private fun synthesizeHiHat(t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        val env = exp(-t * 50)

        // Metallic noise
        val noise = (Math.random() * 2 - 1)

        // High-frequency ring
        val ring = sin(2 * PI * 8000 * t) * 0.3 + sin(2 * PI * 10000 * t) * 0.2

        return (noise * 0.7 + ring * 0.3) * env * velocity * 0.4
    }

    private fun synthesizeClap(t: Double, totalSamples: Int, currentSample: Int, velocity: Float): Double {
        // Multiple noise bursts
        val env1 = if (t < 0.01) exp(-t * 100) else 0.0
        val env2 = if (t > 0.01 && t < 0.02) exp(-(t - 0.01) * 100) else 0.0
        val env3 = if (t > 0.02) exp(-(t - 0.02) * 20) else 0.0

        val noise = (Math.random() * 2 - 1)

        return noise * (env1 * 0.6 + env2 * 0.7 + env3 * 0.8) * velocity * 0.7
    }

    private fun adsr(currentSample: Int, totalSamples: Int, attack: Double, decay: Double, sustain: Double, release: Double): Double {
        val position = currentSample.toDouble() / SAMPLE_RATE
        val totalDuration = totalSamples.toDouble() / SAMPLE_RATE
        val releaseStart = totalDuration - release

        return when {
            position < attack -> position / attack
            position < attack + decay -> 1.0 - (1.0 - sustain) * ((position - attack) / decay)
            position < releaseStart -> sustain
            else -> sustain * (1.0 - (position - releaseStart) / release).coerceIn(0.0, 1.0)
        }
    }

    private fun midiNoteToFrequency(midiNote: Int): Double {
        return 440.0 * 2.0.pow((midiNote - 69) / 12.0)
    }

    private fun applyLimiter(buffer: ShortArray) {
        var maxValue = 1
        for (sample in buffer) {
            maxValue = maxOf(maxValue, abs(sample.toInt()))
        }

        if (maxValue > Short.MAX_VALUE * 0.95) {
            val scale = (Short.MAX_VALUE * 0.9 / maxValue).toFloat()
            for (i in buffer.indices) {
                buffer[i] = (buffer[i] * scale).toInt().toShort()
            }
        }
    }

    fun release() {
        stop()
        scope.cancel()
        audioTrack?.release()
        audioTrack = null
    }
}
