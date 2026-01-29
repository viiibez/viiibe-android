package com.viiibe.app.arcade.audio

import android.content.Context
import com.viiibe.app.arcade.data.Song
import com.viiibe.app.arcade.data.SongDifficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages music playback for the Music Speed game.
 * Uses MIDI synthesis for dynamic, responsive music.
 */
class MusicPlayerManager(private val context: Context) {

    private val midiSynth = MidiSynthesizer()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentBpm = MutableStateFlow(120)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private var currentSong: Song? = null
    private var startTimeMs: Long = 0
    private var elapsedTimeMs: Long = 0

    // Map songs to MIDI patterns (0-4)
    // 0: Dance (four on the floor)
    // 1: Funky (syncopated)
    // 2: Driving (rock)
    // 3: Intense (EDM)
    // 4: Chill (groove)
    private val songPatterns = mapOf(
        // The Spinners - upbeat pop patterns
        "spinners_1" to 0,  // Dance
        "spinners_2" to 1,  // Funky
        "spinners_3" to 2,  // Driving

        // Pedal Punk - edgy patterns
        "punk_1" to 2,      // Driving
        "punk_2" to 3,      // Intense
        "punk_3" to 1,      // Funky

        // Cardio Kings - workout patterns
        "cardio_1" to 0,    // Dance
        "cardio_2" to 3,    // Intense
        "cardio_3" to 2,    // Driving

        // Beat Cyclists - rhythmic patterns
        "cyclists_1" to 1,  // Funky
        "cyclists_2" to 0,  // Dance
        "cyclists_3" to 4,  // Chill

        // Rhythm Racers - energetic patterns
        "racers_1" to 3,    // Intense
        "racers_2" to 2,    // Driving
        "racers_3" to 0,    // Dance

        // Tempo Titans - varied patterns
        "tempo_1" to 4,     // Chill
        "tempo_2" to 1,     // Funky
        "tempo_3" to 3      // Intense
    )

    fun initialize() {
        midiSynth.initialize()
    }

    fun loadSong(song: Song) {
        currentSong = song

        // Set BPM based on song difficulty
        val baseBpm = when (song.difficulty) {
            SongDifficulty.EASY -> 100
            SongDifficulty.MEDIUM -> 120
            SongDifficulty.HARD -> 140
            SongDifficulty.EXTREME -> 160
        }

        midiSynth.setBaseBpm(baseBpm)
        _currentBpm.value = baseBpm

        // Set MIDI pattern based on song
        val patternIndex = songPatterns[song.id] ?: 0
        midiSynth.setPattern(patternIndex)
    }

    fun play() {
        if (!_isPlaying.value) {
            _isPlaying.value = true
            startTimeMs = System.currentTimeMillis() - elapsedTimeMs
            midiSynth.play()
        }
    }

    fun pause() {
        if (_isPlaying.value) {
            _isPlaying.value = false
            elapsedTimeMs = System.currentTimeMillis() - startTimeMs
            midiSynth.pause()
        }
    }

    fun stop() {
        _isPlaying.value = false
        elapsedTimeMs = 0
        midiSynth.stop()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clampedSpeed
        midiSynth.setPlaybackSpeed(clampedSpeed)
        _currentBpm.value = midiSynth.currentBpm.value
    }

    fun getElapsedTimeMs(): Long {
        return if (_isPlaying.value) {
            System.currentTimeMillis() - startTimeMs
        } else {
            elapsedTimeMs
        }
    }

    fun getProgress(): Float {
        val song = currentSong ?: return 0f
        val elapsed = getElapsedTimeMs()
        val duration = song.durationMs
        return (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun isEnded(): Boolean {
        val song = currentSong ?: return false
        return getElapsedTimeMs() >= song.durationMs
    }

    fun release() {
        midiSynth.release()
    }
}
