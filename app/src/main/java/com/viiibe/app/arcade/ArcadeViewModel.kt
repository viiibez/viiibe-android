package com.viiibe.app.arcade

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.arcade.audio.MusicPlayerManager
import com.viiibe.app.arcade.data.*
import com.viiibe.app.arcade.engine.AIEngine
import com.viiibe.app.arcade.engine.BikeInputProcessor
import com.viiibe.app.data.database.ArcadeGameDao
import com.viiibe.app.data.database.ViiibeDatabase
import com.viiibe.app.data.database.WorkoutDao
import com.viiibe.app.data.model.ArcadeGameEntity
import com.viiibe.app.data.sync.HistorySyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class ArcadeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ArcadeViewModel"
    }

    // Database access for saving game results
    private val database = ViiibeDatabase.getDatabase(application)
    private val arcadeGameDao: ArcadeGameDao = database.arcadeGameDao()
    private val workoutDao: WorkoutDao = database.workoutDao()
    private val historySyncManager = HistorySyncManager(arcadeGameDao, workoutDao)

    // Current user ID and wallet (set when game is started)
    private var currentUserId: Long = 0
    private var currentWalletAddress: String? = null
    private var statsSyncAllowed: Boolean = true  // Version check for stats sync

    private var currentGame: ArcadeGame? = null
    private var currentDifficulty: AIDifficulty = AIDifficulty.MEDIUM

    // Game state flows
    private val _sprintRaceState = MutableStateFlow(SprintRaceState())
    val sprintRaceState: StateFlow<SprintRaceState> = _sprintRaceState.asStateFlow()

    private val _powerWarState = MutableStateFlow(PowerWarState())
    val powerWarState: StateFlow<PowerWarState> = _powerWarState.asStateFlow()

    private val _rhythmRideState = MutableStateFlow(RhythmRideState())
    val rhythmRideState: StateFlow<RhythmRideState> = _rhythmRideState.asStateFlow()

    private val _zombieEscapeState = MutableStateFlow(ZombieEscapeState())
    val zombieEscapeState: StateFlow<ZombieEscapeState> = _zombieEscapeState.asStateFlow()

    private val _hillClimbState = MutableStateFlow(HillClimbState())
    val hillClimbState: StateFlow<HillClimbState> = _hillClimbState.asStateFlow()

    private val _powerSurgeState = MutableStateFlow(PowerSurgeState())
    val powerSurgeState: StateFlow<PowerSurgeState> = _powerSurgeState.asStateFlow()

    private val _musicSpeedState = MutableStateFlow(MusicSpeedState())
    val musicSpeedState: StateFlow<MusicSpeedState> = _musicSpeedState.asStateFlow()

    private val _musicSpeedProgress = MutableStateFlow(MusicSpeedProgress())
    val musicSpeedProgress: StateFlow<MusicSpeedProgress> = _musicSpeedProgress.asStateFlow()

    private val _paperRouteState = MutableStateFlow(PaperRouteState())
    val paperRouteState: StateFlow<PaperRouteState> = _paperRouteState.asStateFlow()

    // Input processor for detecting bike actions
    private val bikeInputProcessor = BikeInputProcessor()

    // AI Engine for opponent behavior
    private val aiEngine = AIEngine()

    // Music player for Music Speed game
    private val musicPlayer = MusicPlayerManager(application.applicationContext)

    init {
        musicPlayer.initialize()
    }

    // Game loop job
    private var gameLoopJob: Job? = null
    private var countdownJob: Job? = null

    // Frame timing
    private val frameDelayMs = 16L  // ~60 FPS
    private var lastFrameTime = 0L

    fun initializeGame(game: ArcadeGame, difficulty: AIDifficulty) {
        currentGame = game
        currentDifficulty = difficulty

        // Cancel any existing game loop
        gameLoopJob?.cancel()
        countdownJob?.cancel()

        when (game) {
            ArcadeGame.SPRINT_RACE -> initializeSprintRace(difficulty)
            ArcadeGame.POWER_WAR -> initializePowerWar(difficulty)
            ArcadeGame.RHYTHM_RIDE -> initializeRhythmRide(difficulty)
            ArcadeGame.ZOMBIE_ESCAPE -> initializeZombieEscape(difficulty)
            ArcadeGame.HILL_CLIMB -> initializeHillClimb(difficulty)
            ArcadeGame.POWER_SURGE -> initializePowerSurge(difficulty)
            ArcadeGame.MUSIC_SPEED -> initializeMusicSpeed(difficulty)
            ArcadeGame.PAPER_ROUTE -> initializePaperRoute(difficulty)
        }
    }

    private fun initializeMusicSpeed(difficulty: AIDifficulty) {
        // Select default artist if none selected
        val defaultArtist = MusicLibrary.getArtistById("default")
        _musicSpeedState.value = MusicSpeedState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 180_000),
            selectedArtist = defaultArtist,
            inSongSelect = true
        )
    }

    private fun initializeZombieEscape(difficulty: AIDifficulty) {
        // Zombie base speed in km/h - scales with difficulty
        // Player power requirements to match zombie speed (at game start):
        // EASY: 12 km/h = 200W (comfortable endurance), MEDIUM: 14 km/h = 233W (tempo)
        // HARD: 16 km/h = 267W (threshold), EXTREME: 18 km/h = 300W (VO2max)
        // Note: Zombies accelerate over time, requiring progressively higher power
        val zombieSpeed = when (difficulty) {
            AIDifficulty.EASY -> 12f      // ~200W to match initially
            AIDifficulty.MEDIUM -> 14f    // ~233W to match initially
            AIDifficulty.HARD -> 16f      // ~267W to match initially
            AIDifficulty.EXTREME -> 18f   // ~300W to match initially
        }
        val zombieCount = when (difficulty) {
            AIDifficulty.EASY -> 2
            AIDifficulty.MEDIUM -> 3
            AIDifficulty.HARD -> 4
            AIDifficulty.EXTREME -> 5
        }

        _zombieEscapeState.value = ZombieEscapeState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 120_000),
            zombieSpeed = zombieSpeed,
            zombieCount = zombieCount,
            zombieDistance = -30f,  // Zombies start 30m behind player
            powerUps = generatePowerUps()
        )
    }

    private fun generatePowerUps(): List<PowerUp> {
        return (0..8).map { i ->
            PowerUp(
                id = i,
                position = 100f + i * 150f + Random.nextFloat() * 50f,
                type = PowerUpType.values().random()
            )
        }
    }

    private fun initializeHillClimb(difficulty: AIDifficulty) {
        val targetAltitude = when (difficulty) {
            AIDifficulty.EASY -> 500f
            AIDifficulty.MEDIUM -> 750f
            AIDifficulty.HARD -> 1000f
            AIDifficulty.EXTREME -> 1500f
        }

        val checkpoints = listOf(
            Checkpoint(targetAltitude * 0.25f, "Base Camp"),
            Checkpoint(targetAltitude * 0.5f, "Halfway Point"),
            Checkpoint(targetAltitude * 0.75f, "Final Push"),
            Checkpoint(targetAltitude, "Summit!")
        )

        _hillClimbState.value = HillClimbState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 180_000),
            targetAltitude = targetAltitude,
            checkpoints = checkpoints,
            grade = 5f
        )
    }

    private fun initializePowerSurge(difficulty: AIDifficulty) {
        val targetCount = when (difficulty) {
            AIDifficulty.EASY -> 15
            AIDifficulty.MEDIUM -> 20
            AIDifficulty.HARD -> 25
            AIDifficulty.EXTREME -> 30
        }

        _powerSurgeState.value = PowerSurgeState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 90_000),
            targets = generateTargets(targetCount),
            totalTargets = targetCount
        )
    }

    private fun generateTargets(count: Int): List<PowerTarget> {
        return (0 until count).map { i ->
            val size = when {
                i % 10 == 9 -> TargetSize.BOSS
                i % 5 == 4 -> TargetSize.LARGE
                i % 3 == 2 -> TargetSize.MEDIUM
                else -> TargetSize.SMALL
            }
            PowerTarget(
                id = i,
                x = Random.nextFloat() * 0.8f + 0.1f,
                y = Random.nextFloat() * 0.6f + 0.2f,
                size = size,
                health = size.healthMultiplier,
                maxHealth = size.healthMultiplier
            )
        }
    }

    private fun initializeSprintRace(difficulty: AIDifficulty) {
        val aiCount = when (difficulty) {
            AIDifficulty.EASY -> 1
            AIDifficulty.MEDIUM -> 2
            AIDifficulty.HARD -> 3
            AIDifficulty.EXTREME -> 3
        }

        val aiPlayers = (0 until aiCount).map { i ->
            AIRacerState(
                player = AIPlayer.generate(i, difficulty),
                position = 0f,
                lane = i % 3,
                speed = 0f
            )
        }

        _sprintRaceState.value = SprintRaceState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 90_000),
            playerLane = 1,
            aiPlayers = aiPlayers,
            obstacles = generateInitialObstacles()
        )
    }

    private fun initializePowerWar(difficulty: AIDifficulty) {
        _powerWarState.value = PowerWarState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 45_000),
            aiPlayer = AIPlayer.generate(0, difficulty, AIPersonality.AGGRESSIVE)
        )
    }

    private fun initializeRhythmRide(difficulty: AIDifficulty) {
        // BPM affects note scroll speed - higher = faster notes
        val bpm = when (difficulty) {
            AIDifficulty.EASY -> 100
            AIDifficulty.MEDIUM -> 120
            AIDifficulty.HARD -> 140
            AIDifficulty.EXTREME -> 160
        }

        // Cadence range varies by difficulty
        val (minCadence, maxCadence) = when (difficulty) {
            AIDifficulty.EASY -> 60 to 80       // Comfortable range
            AIDifficulty.MEDIUM -> 60 to 90    // Wider range
            AIDifficulty.HARD -> 55 to 100     // Challenging range
            AIDifficulty.EXTREME -> 50 to 110  // Full spectrum
        }

        // Hold duration (how long you need to maintain each cadence)
        val holdBeats = when (difficulty) {
            AIDifficulty.EASY -> 6      // More time to adjust
            AIDifficulty.MEDIUM -> 4    // Standard
            AIDifficulty.HARD -> 3      // Quick changes
            AIDifficulty.EXTREME -> 2   // Rapid fire
        }

        _rhythmRideState.value = RhythmRideState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 120_000),
            targetCadences = generateCadenceTargets(bpm, 120_000, minCadence, maxCadence, holdBeats),
            bpm = bpm
        )
    }

    private fun generateInitialObstacles(): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        var position = 0.15f  // Start obstacles ahead

        repeat(30) { i ->
            position += Random.nextFloat() * 0.08f + 0.04f
            if (position < 1.5f) {  // Don't generate too far ahead
                obstacles.add(
                    Obstacle(
                        id = i,
                        position = position,
                        lane = Random.nextInt(3),
                        type = ObstacleType.values().random()
                    )
                )
            }
        }
        return obstacles
    }

    private fun generateCadenceTargets(
        bpm: Int,
        durationMs: Long,
        minCadence: Int = 60,
        maxCadence: Int = 90,
        holdBeats: Int = 4
    ): List<CadenceTarget> {
        val targets = mutableListOf<CadenceTarget>()
        val beatDurationMs = (60_000 / bpm).toLong()
        var currentTimeMs = 3000L  // Start after 3 seconds (give time to prepare)

        // Start at a comfortable middle cadence
        val midCadence = (minCadence + maxCadence) / 2
        var currentCadence = midCadence

        // Track song structure for variety (intro, build, peak, outro)
        val introDurationMs = 15_000L
        val outroDurationMs = 10_000L
        val peakStartMs = durationMs / 2

        while (currentTimeMs < durationMs - outroDurationMs) {
            // Determine section and adjust patterns accordingly
            val isIntro = currentTimeMs < introDurationMs
            val isPeak = currentTimeMs >= peakStartMs && currentTimeMs < durationMs - outroDurationMs
            val isOutro = currentTimeMs >= durationMs - outroDurationMs

            // During intro: gradual warmup from middle cadence
            // During peak: larger variations, use full range
            // During outro: bring it back down

            val targetCadenceForSection = when {
                isIntro -> {
                    // Warm up: start at mid, gradually introduce variety
                    val progress = currentTimeMs.toFloat() / introDurationMs
                    val range = ((maxCadence - minCadence) * progress * 0.5f).toInt()
                    currentCadence + listOf(-range/2, 0, range/2).random()
                }
                isPeak -> {
                    // Peak section: full range with bigger jumps
                    val change = listOf(-10, -5, 0, 5, 10).random()
                    currentCadence + change
                }
                else -> {
                    // Normal section: moderate changes
                    val change = listOf(-5, 0, 0, 5, 5).random()
                    currentCadence + change
                }
            }.coerceIn(minCadence, maxCadence)

            currentCadence = targetCadenceForSection

            // Add the target
            targets.add(
                CadenceTarget(
                    targetCadence = currentCadence,
                    startTimeMs = currentTimeMs,
                    durationMs = beatDurationMs * holdBeats
                )
            )
            currentTimeMs += beatDurationMs * holdBeats

            // Occasionally add a short rest (no target) - more common during easier sections
            val restChance = if (isPeak) 0.05f else 0.12f
            if (Random.nextFloat() < restChance) {
                currentTimeMs += beatDurationMs * 2
            }
        }

        // Outro: bring cadence back to comfortable level
        while (currentTimeMs < durationMs - 5000) {
            currentCadence = ((currentCadence + midCadence) / 2)  // Gradually return to middle
            targets.add(
                CadenceTarget(
                    targetCadence = currentCadence,
                    startTimeMs = currentTimeMs,
                    durationMs = beatDurationMs * (holdBeats + 2)  // Longer holds during cooldown
                )
            )
            currentTimeMs += beatDurationMs * (holdBeats + 2)
        }

        return targets
    }

    fun startGame() {
        when (currentGame) {
            ArcadeGame.SPRINT_RACE -> startCountdown(_sprintRaceState) { startSprintRaceLoop() }
            ArcadeGame.POWER_WAR -> startCountdown(_powerWarState) { startPowerWarLoop() }
            ArcadeGame.RHYTHM_RIDE -> startCountdown(_rhythmRideState) { startRhythmRideLoop() }
            ArcadeGame.ZOMBIE_ESCAPE -> startCountdown(_zombieEscapeState) { startZombieEscapeLoop() }
            ArcadeGame.HILL_CLIMB -> startCountdown(_hillClimbState) { startHillClimbLoop() }
            ArcadeGame.POWER_SURGE -> startCountdown(_powerSurgeState) { startPowerSurgeLoop() }
            ArcadeGame.MUSIC_SPEED -> startCountdown(_musicSpeedState) { startMusicSpeedLoop() }
            ArcadeGame.PAPER_ROUTE -> startCountdown(_paperRouteState) { startPaperRouteLoop() }
            null -> {}
        }
    }

    private fun <T> startCountdown(
        stateFlow: MutableStateFlow<T>,
        updateState: (T, GameState) -> T,
        onComplete: () -> Unit
    ) where T : Any {
        countdownJob = viewModelScope.launch {
            val initialState = stateFlow.value
            val gameState = when (initialState) {
                is SprintRaceState -> initialState.gameState
                is PowerWarState -> initialState.gameState
                is RhythmRideState -> initialState.gameState
                is ZombieEscapeState -> initialState.gameState
                is HillClimbState -> initialState.gameState
                is PowerSurgeState -> initialState.gameState
                is MusicSpeedState -> initialState.gameState
                is PaperRouteState -> initialState.gameState
                else -> return@launch
            }

            // Countdown from 3
            for (count in 3 downTo 1) {
                stateFlow.value = updateState(
                    stateFlow.value,
                    gameState.copy(phase = GamePhase.COUNTDOWN, countdownValue = count)
                )
                delay(1000)
            }

            // Start game
            stateFlow.value = updateState(
                stateFlow.value,
                gameState.copy(phase = GamePhase.PLAYING, elapsedTimeMs = 0)
            )
            onComplete()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> startCountdown(stateFlow: MutableStateFlow<T>, onComplete: () -> Unit) {
        startCountdown(stateFlow, { state, newGameState ->
            when (state) {
                is SprintRaceState -> state.copy(gameState = newGameState) as T
                is PowerWarState -> state.copy(gameState = newGameState) as T
                is RhythmRideState -> state.copy(gameState = newGameState) as T
                is ZombieEscapeState -> state.copy(gameState = newGameState) as T
                is HillClimbState -> state.copy(gameState = newGameState) as T
                is PowerSurgeState -> state.copy(gameState = newGameState) as T
                is MusicSpeedState -> state.copy(gameState = newGameState) as T
                is PaperRouteState -> state.copy(gameState = newGameState) as T
                else -> state
            }
        }, onComplete)
    }

    private fun startSprintRaceLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime

                updateSprintRace(deltaMs)

                delay(frameDelayMs)
            }
        }
    }

    private fun startPowerWarLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime

                updatePowerWar(deltaMs)

                delay(frameDelayMs)
            }
        }
    }

    private fun startRhythmRideLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime

                updateRhythmRide(deltaMs)

                delay(frameDelayMs)
            }
        }
    }

    private fun updateSprintRace(deltaMs: Long) {
        val state = _sprintRaceState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs

        // Check if game is over
        if (newElapsedTime >= state.gameState.gameDurationMs) {
            _sprintRaceState.value = state.copy(
                gameState = state.gameState.copy(
                    phase = GamePhase.FINISHED,
                    elapsedTimeMs = state.gameState.gameDurationMs
                )
            )
            gameLoopJob?.cancel()
            return
        }

        // Get current bike action
        val action = bikeInputProcessor.currentAction

        // =======================================================================
        // CYCLING PHYSICS: Speed Calculation Based on Power
        // =======================================================================
        //
        // In real cycling, POWER (watts) is the key metric for speed:
        // - Power = Torque x Angular Velocity, which on a bike means:
        //   Power is proportional to Resistance x Cadence
        // - Higher resistance at same cadence = more power = faster
        // - Higher cadence at same resistance = more power = faster
        // - Spinning fast at low resistance (low power) is SLOWER than
        //   grinding at high resistance (high power)
        //
        // Typical power outputs:
        // - Recreational cyclist: 100-150W (comfortable pace)
        // - Fit cyclist: 200-300W (good sustained effort)
        // - Pro cyclist: 300-400W+ (race pace)
        // - Sprint effort: 400-600W+ (short bursts)
        //
        // Speed Formula (simplified for gameplay):
        // On flat ground, speed is proportional to the cube root of power
        // due to air drag (Power proportional to velocity cubed).
        // For gameplay, we use a hybrid formula for better responsiveness.
        // =======================================================================

        val playerMetrics = bikeInputProcessor.lastMetrics
        val playerPower = playerMetrics.power.toFloat()

        // Convert power to speed using cycling physics
        // Reference points:
        //   100W  -> approx 20 km/h (12.4 mph) - easy recreational pace
        //   150W  -> approx 25 km/h (15.5 mph) - moderate effort
        //   200W  -> approx 30 km/h (18.6 mph) - solid workout
        //   250W  -> approx 34 km/h (21.1 mph) - hard effort
        //   300W  -> approx 38 km/h (23.6 mph) - very hard
        //   350W+ -> approx 42 km/h (26.1 mph) - sprint/race pace
        //
        // Game speed: 150W gives a normalized speed of 1.0
        val basePowerSpeed = calculateSpeedFromPower(playerPower)
        var playerSpeed = basePowerSpeed

        // =======================================================================
        // BOOST MECHANIC: Power Burst Detection
        // =======================================================================
        // A power burst (sustained high power > 250W) triggers a boost.
        // This rewards actual hard effort, not just spinning fast at low resistance.
        // Boost gives 50% speed increase for a short duration.
        // =======================================================================
        val hasBoost = action is BikeAction.PowerBurst && state.boostCooldownMs <= 0
        if (hasBoost) {
            // Boost multiplier: 50% speed increase
            playerSpeed *= 1.5f
        }

        // Handle lane changes from resistance changes
        // Quick resistance adjustments allow dodging obstacles
        var playerLane = state.playerLane
        when (action) {
            is BikeAction.ResistanceUp -> {
                // Jump action (for hurdles) AND move up a lane
                if (playerLane > 0) {
                    playerLane = playerLane - 1
                }
            }
            is BikeAction.ResistanceDown -> {
                // Slide action (for puddles) AND move down a lane
                if (playerLane < 2) {
                    playerLane = playerLane + 1
                }
            }
            else -> {}
        }

        // =======================================================================
        // POSITION & DISTANCE UPDATE
        // =======================================================================
        // Position is the race progress (0 to 1+)
        // Distance is displayed in meters for player feedback
        // Speed units translate to realistic cycling speeds
        val deltaSeconds = deltaMs / 1000f
        val newPlayerPosition = state.playerPosition + (playerSpeed * deltaSeconds * 0.1f)
        // Distance in meters: speed 1.0 at 150W is approx 25 km/h = approx 7 m/s
        val newDistance = state.distanceMeters + (playerSpeed * deltaSeconds * 7f)

        // =======================================================================
        // AI OPPONENT PHYSICS
        // =======================================================================
        // AI opponents use the SAME physics formula as the player.
        // Their speed is determined by their power output (generated by AIEngine),
        // NOT just their cadence. This ensures fair competition where power matters.
        // =======================================================================
        val updatedAiPlayers = state.aiPlayers.map { aiRacer ->
            val aiMetrics = aiEngine.generateMetrics(
                aiRacer.player,
                state.gameState.elapsedTimeMs,
                newPlayerPosition  // AI adapts to player position (rubber-banding)
            )
            // AI speed uses the same power-based formula as the player
            val aiPower = aiMetrics.power.toFloat()
            val aiSpeed = calculateSpeedFromPower(aiPower)
            val newAiPosition = aiRacer.position + (aiSpeed * deltaSeconds * 0.1f)

            aiRacer.copy(
                position = newAiPosition,
                speed = aiSpeed,
                metrics = aiMetrics
            )
        }

        // =======================================================================
        // OBSTACLE COLLISION & SCORING
        // =======================================================================
        // Score rewards:
        // - Avoiding obstacles: +100 points
        // - Power bonus: Extra points for high power output (encourages effort)
        // - Hit penalty: -50 points
        // =======================================================================
        var newScore = state.score
        val updatedObstacles = state.obstacles.map { obstacle ->
            if (!obstacle.passed && obstacle.position < newPlayerPosition) {
                // Check if player avoided the obstacle
                val avoided = obstacle.lane != playerLane ||
                        (obstacle.type == ObstacleType.HURDLE && action is BikeAction.ResistanceUp) ||
                        (obstacle.type == ObstacleType.PUDDLE && action is BikeAction.ResistanceDown)

                if (avoided) {
                    newScore += 100
                    // Power bonus: Extra points for maintaining high power while dodging
                    // Rewards cyclists who can handle obstacles without losing power
                    if (playerPower >= 200) {
                        newScore += ((playerPower - 200) / 10).toInt()  // +1 per 10W above 200W
                    }
                } else {
                    // Hit obstacle - penalty
                    newScore = max(0, newScore - 50)
                }
                obstacle.copy(passed = true)
            } else {
                obstacle
            }
        }

        // Add more obstacles as player progresses
        val furthestObstacle = updatedObstacles.maxOfOrNull { it.position } ?: 0f
        val newObstacles = if (furthestObstacle < newPlayerPosition + 0.5f) {
            updatedObstacles + Obstacle(
                id = updatedObstacles.size,
                position = furthestObstacle + Random.nextFloat() * 0.08f + 0.06f,
                lane = Random.nextInt(3),
                type = ObstacleType.values().random()
            )
        } else {
            updatedObstacles
        }

        _sprintRaceState.value = state.copy(
            gameState = state.gameState.copy(elapsedTimeMs = newElapsedTime),
            playerPosition = newPlayerPosition,
            playerLane = playerLane,
            playerSpeed = playerSpeed,
            hasBoost = hasBoost,
            boostCooldownMs = if (hasBoost) 3000 else max(0, state.boostCooldownMs - deltaMs),
            aiPlayers = updatedAiPlayers,
            obstacles = newObstacles,
            score = newScore,
            distanceMeters = newDistance
        )
    }

    /**
     * Calculates game speed from power output using realistic cycling physics.
     *
     * CYCLING PHYSICS EXPLANATION:
     * In real cycling, the relationship between power and speed is:
     *   Power = Rolling_Resistance + Air_Drag
     *   Power is approx k1 * v + k2 * v^3
     *
     * At higher speeds, air drag dominates (proportional to v^3), so:
     *   v is approx cube_root(Power / k)
     *
     * For gameplay, we use a hybrid formula that:
     * 1. Gives responsive feel at low power (linear component)
     * 2. Follows realistic physics at higher power (cube root component)
     * 3. Maps to real-world reference speeds
     *
     * Reference power levels (watts) for context:
     * - 0-100W:   Very light effort (recovery, warming up)
     * - 100-150W: Recreational pace (casual cycling)
     * - 150-200W: Moderate effort (fitness ride)
     * - 200-250W: Hard effort (tempo/threshold)
     * - 250-300W: Very hard (race pace for amateurs)
     * - 300-400W: Pro-level sustained effort
     * - 400W+:    Sprint efforts (short duration)
     *
     * @param powerWatts The rider's power output in watts
     * @return Normalized game speed (1.0 = comfortable pace at approx 150W)
     */
    private fun calculateSpeedFromPower(powerWatts: Float): Float {
        // Minimum power threshold - below this, barely moving
        // (represents overcoming static friction/inertia)
        if (powerWatts < 30f) {
            return powerWatts / 100f  // Very slow creep
        }

        // Hybrid formula combining linear responsiveness with realistic physics:
        // - Linear component (0.4 weight): Good game feel, responsive to small changes
        // - Cube root component (0.6 weight): Realistic diminishing returns at high power
        //
        // Normalized so that 150W = speed of 1.0 (comfortable recreational pace)
        val referencePower = 150f

        // Linear component: speed proportional to power
        val linearSpeed = powerWatts / referencePower

        // Cube root component: realistic air drag physics (v proportional to cube_root(P))
        // Calibrated so cube_root(150) / cube_root(150) = 1.0 at reference power
        val cubeRootReference = referencePower.toDouble().pow(1.0/3.0).toFloat()
        val cubeRootSpeed = powerWatts.toDouble().pow(1.0/3.0).toFloat() / cubeRootReference

        // Weighted combination for best gameplay feel
        // More linear at low power (responsive), more cube-root at high power (realistic)
        val linearWeight = 0.4f
        val cubeRootWeight = 0.6f

        return (linearSpeed * linearWeight) + (cubeRootSpeed * cubeRootWeight)
    }


    /**
     * Updates the Power War game state with realistic cycling physics.
     *
     * POWER WAR GAME MECHANICS:
     * =========================
     * This is a tug-of-war style game where sustained power output determines who wins.
     * The game simulates a direct power comparison between player and AI opponent.
     *
     * CYCLING PHYSICS IMPLEMENTED:
     * 1. FTP-Based Fatigue Model (W' Balance)
     *    - FTP = Functional Threshold Power (max sustainable for ~1 hour)
     *    - Above FTP: fatigue accumulates exponentially
     *    - Below FTP: fatigue recovers proportionally
     *
     * 2. AI Difficulty = Different Fitness Levels
     *    - Easy: 120W FTP (casual cyclist)
     *    - Medium: 175W FTP (recreational cyclist)
     *    - Hard: 225W FTP (trained cyclist)
     *    - Extreme: 275W FTP (competitive cyclist)
     *
     * 3. Direct Power Competition
     *    - Net power difference determines rope movement
     *    - 100W advantage = 10% rope movement per second
     *    - Fatigue reduces effective power (up to 60% reduction at max fatigue)
     *
     * STRATEGY IMPLICATIONS:
     * - Steady-state riding just below FTP is sustainable
     * - Surges above FTP build fatigue rapidly
     * - Recovery requires backing off significantly
     * - Pacing is critical - going too hard too early leads to collapse
     */
    private fun updatePowerWar(deltaMs: Long) {
        val state = _powerWarState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs
        val deltaSeconds = deltaMs / 1000f

        // Get player power
        val playerMetrics = bikeInputProcessor.lastMetrics
        val rawPlayerPower = playerMetrics.power.toFloat()

        // ========================================
        // CYCLING PHYSICS: FTP-based fatigue model
        // ========================================
        // FTP (Functional Threshold Power) represents the maximum power a cyclist
        // can sustain for approximately 1 hour. Above FTP, fatigue accumulates
        // rapidly. The rate of fatigue depends on how far above FTP you're working.
        //
        // Player assumed FTP: ~175W (average recreational cyclist)
        // This means:
        //   - Below 175W: sustainable, fatigue slowly recovers
        //   - 175-210W (100-120% FTP): can sustain for minutes, moderate fatigue
        //   - 210-260W (120-150% FTP): anaerobic, rapid fatigue accumulation
        //   - 260W+ (150%+ FTP): maximal effort, very rapid fatigue
        val playerFtp = 175f

        // AI FTP scales with difficulty (represents different fitness levels)
        // Easy: 120W (casual cyclist), Medium: 175W (recreational),
        // Hard: 225W (trained), Extreme: 275W (competitive)
        val aiFtp = when (currentDifficulty) {
            AIDifficulty.EASY -> 120f
            AIDifficulty.MEDIUM -> 175f
            AIDifficulty.HARD -> 225f
            AIDifficulty.EXTREME -> 275f
        }

        // Generate AI power based on their FTP and difficulty
        val aiMetrics = state.aiPlayer?.let {
            aiEngine.generateMetrics(it, newElapsedTime, state.ropePosition)
        } ?: PlayerMetrics(power = aiFtp.toInt())
        val rawAiPower = aiMetrics.power.toFloat()

        // ========================================
        // FATIGUE CALCULATION (W' Balance Model)
        // ========================================
        // Based on the critical power / W' model from cycling science:
        // - W' (pronounced "W prime") is a finite work capacity above FTP
        // - Working above FTP depletes W', working below FTP recovers it
        // - The higher above FTP, the faster W' depletes
        // - Recovery rate depends on how far below FTP you're working

        // Player fatigue update
        val playerIntensityRatio = rawPlayerPower / playerFtp
        val newPlayerFatigue: Float

        if (playerIntensityRatio > 1.0f) {
            // Above FTP: accumulate fatigue
            // Fatigue rate increases exponentially with intensity above FTP
            // At 120% FTP, can sustain ~6-8 min; at 150% FTP, ~1-2 min
            val excessIntensity = playerIntensityRatio - 1.0f
            // Fatigue rate: ~0.006/sec at 120% FTP, ~0.034/sec at 150% FTP
            val fatigueRate = excessIntensity * excessIntensity * 0.15f
            newPlayerFatigue = min(1f, state.playerFatigue + fatigueRate * deltaSeconds)
        } else {
            // Below FTP: recover fatigue
            // Recovery is faster the further below FTP you are
            // At 50% FTP, recover at ~0.03/sec; at 80% FTP, recover at ~0.012/sec
            val recoveryIntensity = 1.0f - playerIntensityRatio
            val recoveryRate = recoveryIntensity * 0.06f
            newPlayerFatigue = max(0f, state.playerFatigue - recoveryRate * deltaSeconds)
        }

        // AI fatigue update (same physics model)
        val aiIntensityRatio = rawAiPower / aiFtp
        val newAiFatigue: Float

        if (aiIntensityRatio > 1.0f) {
            val excessIntensity = aiIntensityRatio - 1.0f
            val fatigueRate = excessIntensity * excessIntensity * 0.15f
            newAiFatigue = min(1f, state.aiFatigue + fatigueRate * deltaSeconds)
        } else {
            val recoveryIntensity = 1.0f - aiIntensityRatio
            val recoveryRate = recoveryIntensity * 0.06f
            newAiFatigue = max(0f, state.aiFatigue - recoveryRate * deltaSeconds)
        }

        // ========================================
        // EFFECTIVE POWER AFTER FATIGUE
        // ========================================
        // Fatigue reduces power output significantly
        // At 100% fatigue, power drops to 40% of intended output
        // This simulates the feeling of "hitting the wall" when glycogen depleted
        val playerFatigueMultiplier = 1f - (state.playerFatigue * 0.6f)
        val aiFatigueMultiplier = 1f - (state.aiFatigue * 0.6f)

        val effectivePlayerPower = rawPlayerPower * playerFatigueMultiplier
        val effectiveAiPower = rawAiPower * aiFatigueMultiplier

        // ========================================
        // TUG-OF-WAR ROPE PHYSICS
        // ========================================
        // The rope position is determined by net power difference
        // Power directly translates to pulling force
        //
        // Rope movement normalized so that:
        // - 50W advantage = slow movement toward winner (~5%/sec)
        // - 100W advantage = moderate movement (~10%/sec)
        // - 200W+ advantage = rapid pull (~20%/sec)
        //
        // Scale factor chosen so a 100W advantage moves rope ~0.1 (10%) per second
        // This means a dominant rider can win a round in ~5 seconds
        // But evenly matched riders can battle for the full duration
        val powerDifference = effectivePlayerPower - effectiveAiPower
        val ropeVelocity = powerDifference / 1000f  // 100W diff = 0.1/sec
        val newRopePosition = (state.ropePosition + ropeVelocity * deltaSeconds).coerceIn(0f, 1f)

        // Note: Anchoring mechanic removed - unrealistic in cycling physics
        // In real cycling, you can't "anchor" to resist force
        // The tug-of-war is purely about sustained power output

        // Check for round win
        var newPhase = state.gameState.phase
        var playerRoundsWon = state.playerRoundsWon
        var aiRoundsWon = state.aiRoundsWon
        var round = state.round
        var resetRope = newRopePosition
        var newPlayerFatigueForReset = newPlayerFatigue
        var newAiFatigueForReset = newAiFatigue

        if (newRopePosition >= 0.95f) {
            // Player wins round
            playerRoundsWon++
            if (playerRoundsWon >= 2) {
                newPhase = GamePhase.FINISHED
                gameLoopJob?.cancel()
            } else {
                round++
                resetRope = 0.5f
                // Partial fatigue recovery between rounds (simulates short rest)
                // Recovery of 20% fatigue represents ~10 second recovery period
                newPlayerFatigueForReset = max(0f, newPlayerFatigue - 0.2f)
                newAiFatigueForReset = max(0f, newAiFatigue - 0.2f)
            }
        } else if (newRopePosition <= 0.05f) {
            // AI wins round
            aiRoundsWon++
            if (aiRoundsWon >= 2) {
                newPhase = GamePhase.FINISHED
                gameLoopJob?.cancel()
            } else {
                round++
                resetRope = 0.5f
                // Partial fatigue recovery between rounds
                newPlayerFatigueForReset = max(0f, newPlayerFatigue - 0.2f)
                newAiFatigueForReset = max(0f, newAiFatigue - 0.2f)
            }
        }

        // Check time limit
        if (newElapsedTime >= state.gameState.gameDurationMs && newPhase == GamePhase.PLAYING) {
            // Time's up - whoever has rope on their side wins
            if (newRopePosition > 0.5f) playerRoundsWon++ else aiRoundsWon++
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        _powerWarState.value = state.copy(
            gameState = state.gameState.copy(phase = newPhase, elapsedTimeMs = newElapsedTime),
            ropePosition = resetRope,
            playerPower = playerMetrics.power,
            aiPower = aiMetrics.power,
            playerFatigue = newPlayerFatigueForReset,
            aiFatigue = newAiFatigueForReset,
            playerAnchored = false,  // Anchoring mechanic removed - not realistic
            aiAnchored = false,      // Anchoring mechanic removed - not realistic
            round = round,
            playerRoundsWon = playerRoundsWon,
            aiRoundsWon = aiRoundsWon
        )
    }

    private fun updateRhythmRide(deltaMs: Long) {
        val state = _rhythmRideState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs

        // Check if game is over
        if (newElapsedTime >= state.gameState.gameDurationMs) {
            _rhythmRideState.value = state.copy(
                gameState = state.gameState.copy(
                    phase = GamePhase.FINISHED,
                    elapsedTimeMs = state.gameState.gameDurationMs
                )
            )
            gameLoopJob?.cancel()
            return
        }

        val playerMetrics = bikeInputProcessor.lastMetrics
        val playerCadence = playerMetrics.cadence
        val playerPower = playerMetrics.power

        // Difficulty-based tolerances (harder = tighter windows)
        val perfectTolerance = when (currentDifficulty) {
            AIDifficulty.EASY -> 7
            AIDifficulty.MEDIUM -> 5
            AIDifficulty.HARD -> 4
            AIDifficulty.EXTREME -> 3
        }
        val goodTolerance = when (currentDifficulty) {
            AIDifficulty.EASY -> 12
            AIDifficulty.MEDIUM -> 10
            AIDifficulty.HARD -> 8
            AIDifficulty.EXTREME -> 6
        }

        // Power multiplier: rewards harder effort
        // 50-100W = 1.0x, 100-150W = 1.25x, 150-200W = 1.5x, 200+ = 2.0x
        val powerMultiplier = when {
            playerPower >= 200 -> 2.0f
            playerPower >= 150 -> 1.5f
            playerPower >= 100 -> 1.25f
            else -> 1.0f
        }

        // Update target hits
        var score = state.score
        var combo = state.combo
        var maxCombo = state.maxCombo
        var perfectHits = state.perfectHits
        var goodHits = state.goodHits
        var misses = state.misses
        var lastHitResult = state.lastHitResult
        var lastHitTimeMs = state.lastHitTimeMs

        val updatedTargets = state.targetCadences.mapIndexed { index, target ->
            if (target.hit != HitResult.PENDING) return@mapIndexed target

            val targetEndTime = target.startTimeMs + target.durationMs

            // Check if this target is active (within its time window)
            if (newElapsedTime >= target.startTimeMs && newElapsedTime <= targetEndTime) {
                val cadenceDiff = abs(playerCadence - target.targetCadence)

                // Determine hit quality based on cadence difference
                val hitResult = when {
                    cadenceDiff <= perfectTolerance -> HitResult.PERFECT
                    cadenceDiff <= goodTolerance -> HitResult.GOOD
                    else -> HitResult.PENDING  // Not matching yet, still has time
                }

                if (hitResult != HitResult.PENDING) {
                    // Calculate combo multiplier: starts giving bonus at combo 2
                    // combo 1 = 1.0x, combo 5 = 1.4x, combo 10 = 1.9x, combo 20 = 2.9x
                    val comboMultiplier = 1.0f + (combo * 0.1f)

                    when (hitResult) {
                        HitResult.PERFECT -> {
                            combo++
                            perfectHits++
                            // Base 100 points * power multiplier * combo multiplier
                            val points = (100 * powerMultiplier * comboMultiplier).toInt()
                            score += points
                            lastHitResult = HitResult.PERFECT
                            lastHitTimeMs = newElapsedTime
                        }
                        HitResult.GOOD -> {
                            combo++
                            goodHits++
                            // Base 50 points * power multiplier * combo multiplier
                            val points = (50 * powerMultiplier * comboMultiplier).toInt()
                            score += points
                            lastHitResult = HitResult.GOOD
                            lastHitTimeMs = newElapsedTime
                        }
                        else -> {}
                    }
                    maxCombo = max(maxCombo, combo)
                    return@mapIndexed target.copy(hit = hitResult)
                }
            } else if (newElapsedTime > targetEndTime && target.hit == HitResult.PENDING) {
                // Target window expired without a hit - MISS
                combo = 0  // Reset combo on miss
                misses++
                lastHitResult = HitResult.MISS
                lastHitTimeMs = newElapsedTime
                return@mapIndexed target.copy(hit = HitResult.MISS)
            }

            target
        }

        // Find current target index (the note currently approaching or active)
        val currentIndex = updatedTargets.indexOfFirst { target ->
            val targetEndTime = target.startTimeMs + target.durationMs
            // Show target from 2 seconds before until 500ms after it ends
            newElapsedTime >= target.startTimeMs - 2000 && newElapsedTime <= targetEndTime + 500
        }.let { if (it < 0) state.currentTargetIndex else it }

        // Clear last hit display after 500ms
        val displayLastHit = if (newElapsedTime - lastHitTimeMs < 500) lastHitResult else null

        _rhythmRideState.value = state.copy(
            gameState = state.gameState.copy(elapsedTimeMs = newElapsedTime),
            targetCadences = updatedTargets,
            currentTargetIndex = currentIndex,
            score = score,
            combo = combo,
            maxCombo = maxCombo,
            perfectHits = perfectHits,
            goodHits = goodHits,
            misses = misses,
            lastHitResult = displayLastHit,
            lastHitTimeMs = lastHitTimeMs,
            powerMultiplier = powerMultiplier,
            currentCadence = playerCadence
        )
    }

    // ===== ZOMBIE ESCAPE =====
    private fun startZombieEscapeLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime
                updateZombieEscape(deltaMs)
                delay(frameDelayMs)
            }
        }
    }

    private fun updateZombieEscape(deltaMs: Long) {
        val state = _zombieEscapeState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs
        val deltaSeconds = deltaMs / 1000f

        // ===== POWER-BASED SPEED PHYSICS =====
        // Realistic cycling physics: power determines speed
        // Reference speeds (km/h): 150W = ~10 (jogging), 250W = ~15 (running), 350W = ~20 (sprinting)
        // Formula derived from cycling power equations, simplified for gameplay
        // Speed (m/s) = power / 60, which gives:
        // 150W = 2.5 m/s (9 km/h), 250W = 4.17 m/s (15 km/h), 350W = 5.83 m/s (21 km/h)
        val playerMetrics = bikeInputProcessor.lastMetrics
        val power = playerMetrics.power.toFloat().coerceAtLeast(0f)
        val basePlayerSpeed = (power / 60f).coerceIn(0f, 8.33f)  // Max ~30 km/h

        // Apply speed boost power-up if active
        var playerSpeed = basePlayerSpeed
        var speedBoostTime = state.speedBoostTimeMs
        var hasSpeedBoost = state.hasSpeedBoost
        if (hasSpeedBoost && speedBoostTime > 0) {
            playerSpeed *= 1.4f  // 40% boost (like drafting or adrenaline surge)
            speedBoostTime -= deltaMs
            if (speedBoostTime <= 0) {
                hasSpeedBoost = false
                speedBoostTime = 0
            }
        }

        // Update player distance (meters)
        val newPlayerDistance = state.playerDistance + playerSpeed * deltaSeconds

        // ===== ZOMBIE SPEED SCALING =====
        // Zombies get progressively faster, requiring sustained higher power to escape
        // Base zombie speed set by difficulty (12-22 km/h = 3.33-6.11 m/s base)
        // Zombies accelerate over time with exponential scaling for pressure
        val elapsedMinutes = newElapsedTime / 60000f
        // Speed increases: +15% at 30s, +35% at 1min, +60% at 1.5min, +90% at 2min
        val timeMultiplier = 1f + (elapsedMinutes * elapsedMinutes * 0.4f)
        // Convert zombie speed from km/h to m/s and apply time scaling
        val zombieSpeedMps = (state.zombieSpeed / 3.6f) * timeMultiplier

        // Zombies relentlessly advance
        var newZombieDistance = state.zombieDistance + zombieSpeedMps * deltaSeconds

        // ===== GAP AND TENSION CALCULATION =====
        val gap = newPlayerDistance - newZombieDistance
        // Heartbeat intensity scales with proximity (more intense as zombies close in)
        // Full intensity when gap < 5m, zero when gap > 30m
        val heartbeatIntensity = ((30f - gap) / 25f).coerceIn(0f, 1f)

        // ===== POWER-UP COLLECTION =====
        var score = state.score
        var closeCallCount = state.closeCallCount
        val updatedPowerUps = state.powerUps.map { powerUp ->
            if (!powerUp.collected && newPlayerDistance >= powerUp.position && newPlayerDistance <= powerUp.position + 5) {
                when (powerUp.type) {
                    PowerUpType.SPEED_BOOST -> {
                        hasSpeedBoost = true
                        speedBoostTime = 6000  // 6 seconds of boosted speed
                    }
                    PowerUpType.ZOMBIE_SLOW -> {
                        // Push zombies back significantly (equivalent to ~5 seconds of lead at moderate pace)
                        newZombieDistance -= 25f
                    }
                    PowerUpType.SCORE_BONUS -> {
                        score += 500
                    }
                }
                powerUp.copy(collected = true)
            } else {
                powerUp
            }
        }

        // ===== CLOSE CALL TRACKING =====
        // Award points for narrowly escaping (gap 3-8 meters) - tracked per-frame to avoid spam
        val wasCloseCall = gap in 3f..8f
        val previousGap = state.playerDistance - state.zombieDistance
        val previousWasCloseCall = previousGap in 3f..8f
        if (wasCloseCall && !previousWasCloseCall) {
            closeCallCount++
            score += 75  // Bonus for close calls
        }

        // ===== SURVIVAL SCORING =====
        // Score based on distance covered (1 point per meter)
        // Bonus points for maintaining high speed
        val distancePoints = (playerSpeed * deltaSeconds).toInt()
        val speedBonus = if (power >= 250) ((power - 200) / 100f * deltaSeconds).toInt() else 0
        score += distancePoints + speedBonus

        // ===== WIN/LOSE CONDITIONS =====
        var newPhase = state.gameState.phase

        // Caught by zombies!
        if (newZombieDistance >= newPlayerDistance) {
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        // Survived the full duration!
        if (newElapsedTime >= state.gameState.gameDurationMs) {
            // Survival bonus scales with remaining gap (more gap = cleaner escape)
            val gapBonus = (gap * 10).toInt().coerceIn(0, 500)
            score += 1000 + gapBonus  // Base survival bonus + gap bonus
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        _zombieEscapeState.value = state.copy(
            gameState = state.gameState.copy(phase = newPhase, elapsedTimeMs = newElapsedTime),
            playerDistance = newPlayerDistance,
            zombieDistance = newZombieDistance,
            playerSpeed = playerSpeed,
            heartbeatIntensity = heartbeatIntensity,
            powerUps = updatedPowerUps,
            hasSpeedBoost = hasSpeedBoost,
            speedBoostTimeMs = speedBoostTime,
            score = score,
            closeCallCount = closeCallCount
        )
    }

    // ===== HILL CLIMB =====
    private fun startHillClimbLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime
                updateHillClimb(deltaMs)
                delay(frameDelayMs)
            }
        }
    }

    private fun updateHillClimb(deltaMs: Long) {
        val state = _hillClimbState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs
        val deltaSeconds = deltaMs / 1000f

        val playerMetrics = bikeInputProcessor.lastMetrics

        // ===========================================
        // REALISTIC CYCLING PHYSICS FOR HILL CLIMBING
        // ===========================================
        //
        // Key formula: Climbing speed = (Power - resistances) / (mass * g * grade_fraction)
        //
        // Real-world reference: 200W on 8% grade for 75kg rider = ~10-12 km/h
        //
        // Physics breakdown:
        // - Power (W) = Force * Velocity
        // - On a climb: Force needed = mass * g * sin(grade) + rolling resistance + air resistance
        // - For steep grades, sin(grade) ~ grade (in decimal)
        // - Climbing velocity = (Power - losses) / (mass * g * grade)

        // Constants
        val riderMass = 75f  // kg (rider + bike)
        val gravity = 9.81f  // m/s^2

        // Update grade based on altitude (gets steeper as you climb - realistic mountain profile)
        // Starts at base grade, increases toward summit
        val progressRatio = state.altitude / state.targetAltitude
        // Grade profile: base grade at start, steepens exponentially toward summit
        val baseGrade = when (currentDifficulty) {
            AIDifficulty.EASY -> 4f
            AIDifficulty.MEDIUM -> 6f
            AIDifficulty.HARD -> 8f
            AIDifficulty.EXTREME -> 10f
        }
        // Exponential steepening toward summit (like a real mountain)
        val newGrade = baseGrade + (progressRatio * progressRatio) * 10f
        val gradeFraction = newGrade / 100f  // Convert % to decimal

        // Gear simulation via resistance input
        // Lower resistance = easier gear = higher cadence needed for same power
        // Higher resistance = harder gear = lower cadence but more force per pedal
        // Optimal climbing is finding the right gear for the grade
        val gearRatio = (playerMetrics.resistance / 50f).coerceIn(0.5f, 2.0f)  // 0.5x to 2x

        // Power calculation
        // Real power from the bike sensor (or estimated from cadence if no power meter)
        var effectivePower = playerMetrics.power.toFloat()

        // If power is 0 or very low, estimate from cadence (for bikes without power meters)
        if (effectivePower < 30f) {
            // Rough estimate: Power ~ cadence * resistance_factor * 2
            // At 70 RPM with moderate resistance (50), this gives ~140W
            effectivePower = playerMetrics.cadence * gearRatio * 2f
        }

        // Apply fatigue penalty to effective power
        var newFatigue = state.fatigueLevel
        val fatigueMultiplier = 1f - newFatigue * 0.4f  // Up to 40% power loss at max fatigue
        effectivePower *= fatigueMultiplier

        // Power losses
        // Rolling resistance: ~10-20W at climbing speeds
        val rollingResistancePower = 15f
        // Air resistance: minimal at low climbing speeds, V^3 relationship
        // At 10 km/h (2.78 m/s), air resistance ~ 5-10W
        val currentSpeedMs = (state.speed / 3.6f).coerceAtLeast(0f)  // Convert km/h to m/s
        val airResistancePower = 0.5f * 0.3f * 1.0f * currentSpeedMs * currentSpeedMs * currentSpeedMs

        // Net power available for climbing
        val netPower = (effectivePower - rollingResistancePower - airResistancePower).coerceAtLeast(0f)

        // ===========================================
        // CLIMBING SPEED CALCULATION
        // ===========================================
        // v = P / (m * g * grade)
        //
        // Example: 200W, 75kg, 8% grade
        // v = 200 / (75 * 9.81 * 0.08) = 200 / 58.86 = 3.4 m/s = 12.2 km/h

        // Calculate theoretical climbing speed (m/s)
        val gravityForce = riderMass * gravity * gradeFraction
        val theoreticalSpeedMs = if (gravityForce > 0.1f) {
            netPower / gravityForce
        } else {
            netPower / 10f  // Flat ground fallback
        }

        // Convert to km/h for display
        val theoreticalSpeedKmh = theoreticalSpeedMs * 3.6f

        // Can go backwards (roll back) if not putting out enough power!
        // Minimum power to hold position = m * g * grade (no speed)
        val holdPower = riderMass * gravity * gradeFraction * currentSpeedMs.coerceAtLeast(0.1f)
        val canHoldPosition = effectivePower >= holdPower * 0.5f

        // Calculate actual speed with momentum smoothing
        val targetSpeed = if (canHoldPosition) {
            theoreticalSpeedKmh.coerceIn(0f, 25f)  // Max 25 km/h climbing
        } else {
            // Rolling backwards! Speed depends on how much under threshold
            val deficit = (holdPower * 0.5f - effectivePower) / (holdPower * 0.5f + 1f)
            -deficit * 5f  // Max rollback ~5 km/h
        }

        // Momentum smoothing (inertia - can't instantly change speed)
        val momentumFactor = 0.92f  // Higher = more momentum, slower response
        val newMomentum = state.momentum * momentumFactor + targetSpeed * (1f - momentumFactor)
        val effectiveSpeed = (targetSpeed * 0.4f + newMomentum * 0.6f)

        // ===========================================
        // ALTITUDE GAIN
        // ===========================================
        // For climbing, vertical gain = horizontal distance * grade
        // But we simplify: altitude gain = speed * grade * time
        // This assumes road distance, and grade gives us elevation per unit road distance

        val speedMs = (effectiveSpeed / 3.6f).coerceIn(-2f, 7f)  // m/s, limit rollback
        val altitudeGain = speedMs * gradeFraction * deltaSeconds * 100f  // Scale up for gameplay
        var newAltitude = (state.altitude + altitudeGain).coerceIn(0f, state.targetAltitude + 10f)

        // ===========================================
        // FATIGUE SYSTEM
        // ===========================================
        // Fatigue increases when pushing above sustainable power (FTP ~ 200W typical)
        // Fatigue recovers when easing off

        val ftpThreshold = 180f  // Sustainable power threshold
        val powerRatio = effectivePower / ftpThreshold

        if (powerRatio > 1.2f) {
            // Pushing hard above FTP - rapid fatigue accumulation
            val fatigueRate = (powerRatio - 1.0f) * 0.0008f
            newFatigue = min(1f, newFatigue + fatigueRate * deltaMs / 16f)
        } else if (powerRatio > 1.0f) {
            // Slightly above FTP - slow fatigue accumulation
            val fatigueRate = (powerRatio - 1.0f) * 0.0003f
            newFatigue = min(1f, newFatigue + fatigueRate * deltaMs / 16f)
        } else if (powerRatio < 0.7f) {
            // Easy spinning - good recovery
            newFatigue = max(0f, newFatigue - 0.0004f * deltaMs / 16f)
        } else {
            // Moderate effort - slight recovery
            newFatigue = max(0f, newFatigue - 0.0001f * deltaMs / 16f)
        }

        // ===========================================
        // CHECKPOINT SYSTEM
        // ===========================================
        var score = state.score
        var nextCheckpoint = state.nextCheckpoint
        val updatedCheckpoints = state.checkpoints.mapIndexed { index, checkpoint ->
            if (!checkpoint.reached && newAltitude >= checkpoint.altitude && index == nextCheckpoint) {
                // Bonus points scale with checkpoint difficulty
                val checkpointBonus = 250 * (index + 1)
                // Time bonus: faster = more points
                val timeBonus = max(0, (180 - (newElapsedTime / 1000).toInt()) * 2)
                score += checkpointBonus + timeBonus
                nextCheckpoint = index + 1
                checkpoint.copy(reached = true)
            } else {
                checkpoint
            }
        }

        // Continuous scoring based on climbing (VAM - vertical meters per hour concept)
        if (effectiveSpeed > 0) {
            val vamPoints = (altitudeGain * 0.1f).toInt()  // Points for altitude gained
            score += vamPoints
        }

        // ===========================================
        // WIN/LOSE CONDITIONS
        // ===========================================
        var newPhase = state.gameState.phase

        // Reached summit!
        if (newAltitude >= state.targetAltitude) {
            // Summit bonus based on time remaining
            val timeRemainingMs = state.gameState.gameDurationMs - newElapsedTime
            val timeBonusPoints = (timeRemainingMs / 100).toInt()  // 10 points per second remaining
            score += 1000 + timeBonusPoints
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        // Time's up
        if (newElapsedTime >= state.gameState.gameDurationMs) {
            // Partial credit for altitude achieved
            val partialCredit = ((newAltitude / state.targetAltitude) * 500).toInt()
            score += partialCredit
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        _hillClimbState.value = state.copy(
            gameState = state.gameState.copy(phase = newPhase, elapsedTimeMs = newElapsedTime),
            altitude = newAltitude,
            grade = newGrade,
            speed = effectiveSpeed,
            momentum = newMomentum,
            fatigueLevel = newFatigue,
            checkpoints = updatedCheckpoints,
            nextCheckpoint = nextCheckpoint,
            score = score
        )
    }

    // ===== POWER SURGE =====
    private fun startPowerSurgeLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime
                updatePowerSurge(deltaMs)
                delay(frameDelayMs)
            }
        }
    }

    private fun updatePowerSurge(deltaMs: Long) {
        val state = _powerSurgeState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs
        val deltaSeconds = deltaMs / 1000f

        val playerMetrics = bikeInputProcessor.lastMetrics
        val currentPower = playerMetrics.power
        val currentCadence = playerMetrics.cadence
        val currentResistance = playerMetrics.resistance

        // ===== AIMING SYSTEM =====
        // Resistance controls horizontal aim (0-100 resistance -> 0.1-0.9 screen position)
        // Smooth the aim movement for better feel
        val targetAimX = (currentResistance / 100f).coerceIn(0.1f, 0.9f)
        val aimSmoothSpeed = 4f * deltaSeconds  // Aim moves smoothly
        val newAimX = state.aimX + (targetAimX - state.aimX) * aimSmoothSpeed

        // Cadence controls vertical aim (40-100 cadence -> 0.8-0.2 screen position)
        // Low cadence = aim low (0.8), high cadence = aim high (0.2)
        val normalizedCadence = ((currentCadence - 40f) / 60f).coerceIn(0f, 1f)
        val targetAimY = 0.8f - normalizedCadence * 0.6f  // Invert so high cadence = aim up
        val newAimY = state.aimY + (targetAimY - state.aimY) * aimSmoothSpeed

        // ===== CHARGE SYSTEM =====
        // Power directly fills charge meter:
        // - 100W = slow charge (0.33/sec, 3 sec to full)
        // - 200W = moderate charge (0.67/sec, 1.5 sec to full)
        // - 300W+ = fast charge (1.0/sec, 1 sec to full)
        // - 400W+ = very fast (1.33/sec)
        var chargeLevel = state.chargeLevel
        val chargeRate = (currentPower / 300f).coerceIn(0f, 1.5f)  // 300W = 1.0 charge/sec
        chargeLevel = min(1f, chargeLevel + chargeRate * deltaSeconds)

        // Track peak power during charge (for bonus damage)
        var peakChargePower = state.peakChargePower
        if (currentPower > peakChargePower) {
            peakChargePower = currentPower
        }

        // Visual feedback: glow intensity increases with charge
        val chargeGlowIntensity = chargeLevel * (0.5f + 0.5f * sin(newElapsedTime / 100f).toFloat())

        // Blast is ready at 25% charge (lower threshold for more frequent shooting)
        val blastReady = chargeLevel >= 0.25f

        // ===== BLAST DETECTION =====
        // Fire by quickly reducing power (ease off pedaling) - like "releasing" the energy
        // OR by a quick resistance spike (pull trigger)
        val powerDrop = state.lastPower - currentPower
        val shouldBlast = blastReady &&
                          (newElapsedTime - state.lastBlastTimeMs > 400) &&  // 400ms cooldown
                          (powerDrop > 30 || bikeInputProcessor.currentAction is BikeAction.ResistanceUp)

        // ===== PROCESS BLAST =====
        var score = state.score
        var combo = state.combo
        var maxCombo = state.maxCombo
        var targetsDestroyed = state.targetsDestroyed
        var lastBlastTime = state.lastBlastTimeMs
        var blastInProgress = false
        var blastX = state.blastX
        var blastY = state.blastY
        var blastRadius = state.blastRadius
        var lastHitTargetIds = emptyList<Int>()
        var totalDamageDealt = state.totalDamageDealt
        var shotsFired = state.shotsFired
        var shotsHit = state.shotsHit
        var multiHits = state.multiHits
        var perfectShots = state.perfectShots

        val updatedTargets = if (shouldBlast) {
            // FIRE!
            shotsFired++
            blastInProgress = true
            blastX = newAimX
            blastY = newAimY
            lastBlastTime = newElapsedTime

            // Blast radius based on charge level (0.05 to 0.2 of screen)
            // Higher charge = bigger blast = can hit multiple targets
            blastRadius = 0.05f + chargeLevel * 0.15f

            // Damage based on charge level and peak power
            // At full charge with 300W+, deal maximum damage
            val baseDamage = (chargeLevel * 2f).toInt().coerceAtLeast(1)  // 1-2 base damage
            val powerBonus = if (peakChargePower >= 300) 1 else 0  // +1 damage for high power
            val blastDamage = baseDamage + powerBonus

            // Reset charge
            chargeLevel = 0f
            peakChargePower = 0

            // Find all targets within blast radius and damage them
            val hitTargets = mutableListOf<Int>()
            var anyPerfectHit = false

            val newTargets = state.targets.map { target ->
                if (target.destroyed) return@map target

                // Calculate distance from blast center to target center
                val dx = target.x - blastX
                val dy = target.y - blastY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                // Target hitbox size scales with target size
                val targetRadius = 0.03f * target.size.sizeMultiplier
                val hitDistance = blastRadius + targetRadius

                if (distance <= hitDistance) {
                    // HIT!
                    hitTargets.add(target.id)

                    // Perfect hit = direct center hit (within 0.03)
                    val isPerfectHit = distance <= 0.03f
                    if (isPerfectHit) anyPerfectHit = true

                    // Apply damage (perfect hits do +1)
                    val finalDamage = if (isPerfectHit) blastDamage + 1 else blastDamage
                    totalDamageDealt += finalDamage
                    val newHealth = target.health - finalDamage

                    if (newHealth <= 0) {
                        // Target destroyed!
                        combo++
                        maxCombo = max(maxCombo, combo)

                        // Score calculation:
                        // Base points + combo bonus + perfect bonus
                        var points = target.size.points
                        points = (points * (1f + combo * 0.1f)).toInt()  // 10% bonus per combo
                        if (isPerfectHit) points = (points * 1.5f).toInt()  // 50% perfect bonus

                        score += points
                        targetsDestroyed++

                        target.copy(
                            health = 0,
                            destroyed = true,
                            hitFlashTimeMs = 300,
                            shakeIntensity = 1f
                        )
                    } else {
                        // Damaged but not destroyed
                        target.copy(
                            health = newHealth,
                            hitFlashTimeMs = 200,
                            shakeIntensity = 0.5f
                        )
                    }
                } else {
                    target
                }
            }

            // Update shot stats
            if (hitTargets.isNotEmpty()) {
                shotsHit++
                if (hitTargets.size >= 2) multiHits++
                if (anyPerfectHit) perfectShots++
            } else {
                // Missed! Break combo
                combo = 0
            }

            lastHitTargetIds = hitTargets
            newTargets
        } else {
            // No blast - update target visual states (decay shake/flash)
            state.targets.map { target ->
                target.copy(
                    hitFlashTimeMs = max(0, target.hitFlashTimeMs - deltaMs),
                    shakeIntensity = max(0f, target.shakeIntensity - deltaSeconds * 3f),
                    isNewlySpawned = target.spawnTimeMs > 0 && newElapsedTime - target.spawnTimeMs < 500
                )
            }
        }

        // Combo decay: if no hit for 2 seconds, combo starts dropping
        if (!shouldBlast && newElapsedTime - lastBlastTime > 2000 && combo > 0) {
            // Gradual combo decay instead of instant reset
            if (newElapsedTime - lastBlastTime > 2500) {
                combo = max(0, combo - 1)
            }
        }

        // Clear blast animation after 200ms
        if (state.blastInProgress && newElapsedTime - state.lastBlastTimeMs > 200) {
            blastInProgress = false
        }

        // ===== CHECK WIN/LOSE =====
        var newPhase = state.gameState.phase

        // All targets destroyed!
        if (updatedTargets.all { it.destroyed }) {
            // Completion bonus based on time remaining
            val timeRemaining = state.gameState.gameDurationMs - newElapsedTime
            val timeBonus = (timeRemaining / 1000).toInt() * 10  // 10 points per second left
            score += 500 + timeBonus  // Base completion bonus + time bonus
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        // Time's up
        if (newElapsedTime >= state.gameState.gameDurationMs) {
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        _powerSurgeState.value = state.copy(
            gameState = state.gameState.copy(phase = newPhase, elapsedTimeMs = newElapsedTime),
            chargeLevel = chargeLevel,
            targets = updatedTargets,
            score = score,
            combo = combo,
            maxCombo = maxCombo,
            blastReady = blastReady,
            lastBlastTimeMs = lastBlastTime,
            targetsDestroyed = targetsDestroyed,
            // Aiming
            aimX = newAimX,
            aimY = newAimY,
            // Visual feedback
            blastInProgress = blastInProgress,
            blastX = blastX,
            blastY = blastY,
            blastRadius = blastRadius,
            lastHitTargetIds = lastHitTargetIds,
            chargeGlowIntensity = chargeGlowIntensity,
            // Stats
            totalDamageDealt = totalDamageDealt,
            shotsFired = shotsFired,
            shotsHit = shotsHit,
            multiHits = multiHits,
            perfectShots = perfectShots,
            // Power tracking
            lastPower = currentPower,
            peakChargePower = peakChargePower
        )
    }

    // ===== MUSIC SPEED =====
    private fun startMusicSpeedLoop() {
        // Start the music playback
        musicPlayer.play()

        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime
                updateMusicSpeed(deltaMs)
                delay(frameDelayMs)
            }
        }
    }

    private fun updateMusicSpeed(deltaMs: Long) {
        val state = _musicSpeedState.value
        if (state.gameState.phase != GamePhase.PLAYING) return
        if (state.selectedSong == null) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs
        val deltaSeconds = deltaMs / 1000f

        val playerMetrics = bikeInputProcessor.lastMetrics
        val power = playerMetrics.power

        // =======================================================
        // POWER-TO-SPEED MAPPING
        // =======================================================
        // The core mechanic: your power output controls how fast
        // the music plays. This creates a satisfying connection
        // between effort and musical experience.
        //
        // Target mapping (linear interpolation):
        //   100W = 0.5x speed (slow motion - easy listening)
        //   200W = 1.0x speed (normal - comfortable pace)
        //   300W = 1.5x speed (fast forward - pushing hard)
        //   400W+ = 2.0x speed (max - sprint effort)
        //
        // Below 50W: Music pauses (need minimum effort!)
        // =======================================================

        val minPowerToPlay = 50     // Below this, music pauses
        val slowPower = 100f        // 0.5x speed
        val normalPower = 200f      // 1.0x speed
        val fastPower = 300f        // 1.5x speed
        val maxPower = 400f         // 2.0x speed (cap)

        // Calculate raw target speed based on power
        val targetSpeed = when {
            power < minPowerToPlay -> 0f  // Music pauses - pedal harder!
            power <= slowPower -> {
                // 50W-100W: ramp from 0.25x to 0.5x (getting started)
                val t = (power - minPowerToPlay) / (slowPower - minPowerToPlay)
                0.25f + t * 0.25f
            }
            power <= normalPower -> {
                // 100W-200W: ramp from 0.5x to 1.0x (building momentum)
                val t = (power - slowPower) / (normalPower - slowPower)
                0.5f + t * 0.5f
            }
            power <= fastPower -> {
                // 200W-300W: ramp from 1.0x to 1.5x (pushing it)
                val t = (power - normalPower) / (fastPower - normalPower)
                1.0f + t * 0.5f
            }
            power <= maxPower -> {
                // 300W-400W: ramp from 1.5x to 2.0x (all out!)
                val t = (power - fastPower) / (maxPower - fastPower)
                1.5f + t * 0.5f
            }
            else -> 2.0f  // Cap at 2x even for superhuman efforts
        }

        // =======================================================
        // SMOOTH SPEED TRANSITIONS
        // =======================================================
        // Instant speed changes feel jarring and unmusical.
        // We use exponential smoothing to create fluid transitions
        // that feel natural while still being responsive.
        //
        // - Speed UP: faster response (0.15) - feels rewarding
        // - Speed DOWN: slower response (0.08) - grace period
        // - Music pause: immediate when below threshold
        // =======================================================

        val currentSpeed = state.playbackSpeed
        val playbackSpeed = when {
            targetSpeed == 0f -> 0f  // Immediate pause when no effort
            targetSpeed > currentSpeed -> {
                // Speeding up - fairly responsive (feels rewarding)
                val smoothFactor = 0.15f
                currentSpeed + (targetSpeed - currentSpeed) * smoothFactor
            }
            else -> {
                // Slowing down - more gradual (grace period for recovery)
                val smoothFactor = 0.08f
                currentSpeed + (targetSpeed - currentSpeed) * smoothFactor
            }
        }

        // Sync music player speed with calculated speed
        if (playbackSpeed > 0f) {
            musicPlayer.setPlaybackSpeed(playbackSpeed)
        } else {
            musicPlayer.pause()  // Pause music when power too low
        }

        // Calculate effective BPM (what the music sounds like)
        val baseBpm = state.selectedSong.baseBpm
        val effectiveBpm = (baseBpm * playbackSpeed).toInt()

        // =======================================================
        // SONG PROGRESS
        // =======================================================
        // Progress advances based on playback speed.
        // Higher power = faster progress = finish sooner!
        // =======================================================

        val songDuration = state.selectedSong.durationMs
        val progressIncrement = if (playbackSpeed > 0f) {
            (deltaMs * playbackSpeed) / songDuration
        } else {
            0f  // No progress when music paused
        }
        val newProgress = (state.songProgress + progressIncrement).coerceIn(0f, 1f)

        // =======================================================
        // SCORING SYSTEM
        // =======================================================
        // Score rewards sustained effort, not just peak bursts.
        //
        // Base points scale with playback speed (continuous):
        //   0.5x = 1x points
        //   1.0x = 2x points
        //   1.5x = 3x points
        //   2.0x = 4x points
        //
        // Streak bonus: maintain 1.2x+ speed for bonus multiplier
        // Completion bonus: finish the song for big reward
        // =======================================================

        // Continuous speed multiplier (not discrete tiers)
        val speedMultiplier = if (playbackSpeed > 0f) {
            // Linear scaling: 0.5x -> 1 point, 2.0x -> 4 points
            (playbackSpeed * 2f).coerceIn(0.5f, 4f)
        } else {
            0f  // No points when paused
        }

        val basePointsPerSecond = 10f * state.selectedSong.difficulty.pointMultiplier
        val pointsThisFrame = (basePointsPerSecond * speedMultiplier * deltaSeconds).toInt()

        var newScore = state.score + pointsThisFrame
        var pointsEarned = state.pointsEarned + pointsThisFrame

        // Track speed streaks (maintain 1.2x+ for streaks)
        var speedStreak = state.speedStreak
        if (playbackSpeed >= 1.2f) {
            speedStreak++
            // Streak bonus every ~1 second at 60fps
            if (speedStreak % 60 == 0) {
                val streakBonus = 25 + (speedStreak / 60) * 10  // Growing bonus
                newScore += streakBonus
                pointsEarned += streakBonus
            }
        } else {
            // Streak breaks below 1.2x (but some grace from smoothing)
            if (speedStreak > 0 && playbackSpeed < 1.0f) {
                speedStreak = 0
            }
        }

        // Track max and average speed
        val maxSpeedReached = max(state.maxSpeedReached, playbackSpeed)
        val avgSpeed = if (playbackSpeed > 0f) {
            (state.avgSpeed * 0.99f + playbackSpeed * 0.01f)
        } else {
            state.avgSpeed  // Don't count paused time in average
        }

        // =======================================================
        // AUDIO VISUALIZER
        // =======================================================
        // Visualizer bars react to power output and playback speed,
        // creating a visual representation of your effort.
        // =======================================================

        val visualizerBars = List(20) { i ->
            val powerBase = (power / 300f).coerceIn(0.1f, 1f)
            val speedBase = (playbackSpeed / 2f).coerceIn(0.1f, 1f)
            val combined = (powerBase * 0.6f + speedBase * 0.4f)
            val variation = (sin(System.currentTimeMillis() / 100.0 + i * 0.5) * 0.3f).toFloat()
            (combined + variation + Random.nextFloat() * 0.15f).coerceIn(0.05f, 1f)
        }

        // Check if song is complete
        var newPhase = state.gameState.phase
        if (newProgress >= 1f) {
            // Song completed! Calculate completion time bonus
            musicPlayer.stop()

            // Faster completion = bigger bonus
            // At 1.0x average, song takes normal time
            // At 1.5x average, song takes 2/3 time -> bonus
            val speedBonus = ((avgSpeed - 1f) * 200).toInt().coerceAtLeast(0)
            val completionBonus = (500 * state.selectedSong.difficulty.pointMultiplier).toInt() + speedBonus
            newScore += completionBonus
            pointsEarned += completionBonus

            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()

            // Update progress (high score, total points)
            updateMusicSpeedProgress(state.selectedSong.id, newScore, pointsEarned)
        }

        // Check time limit (shouldn't hit this normally since song progress determines end)
        if (newElapsedTime >= state.gameState.gameDurationMs && newPhase == GamePhase.PLAYING) {
            musicPlayer.stop()
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
            updateMusicSpeedProgress(state.selectedSong.id, newScore, pointsEarned)
        }

        _musicSpeedState.value = state.copy(
            gameState = state.gameState.copy(phase = newPhase, elapsedTimeMs = newElapsedTime),
            playbackSpeed = playbackSpeed,
            currentCadence = playerMetrics.cadence,  // Still track cadence for display
            score = newScore,
            pointsEarned = pointsEarned,
            speedStreak = speedStreak,
            maxSpeedReached = maxSpeedReached,
            avgSpeed = avgSpeed,
            songProgress = newProgress,
            bpm = effectiveBpm,
            visualizerBars = visualizerBars
        )
    }

    private fun updateMusicSpeedProgress(songId: String, score: Int, pointsEarned: Int) {
        val currentProgress = _musicSpeedProgress.value
        val newHighScores = currentProgress.highScores.toMutableMap()

        // Update high score if new score is higher
        val currentHighScore = newHighScores[songId] ?: 0
        if (score > currentHighScore) {
            newHighScores[songId] = score
        }

        _musicSpeedProgress.value = currentProgress.copy(
            totalPoints = currentProgress.totalPoints + pointsEarned,
            highScores = newHighScores,
            totalSongsPlayed = currentProgress.totalSongsPlayed + 1,
            totalPlayTimeMs = currentProgress.totalPlayTimeMs + (_musicSpeedState.value.gameState.elapsedTimeMs)
        )
    }

    fun selectMusicArtist(artist: MusicArtist) {
        _musicSpeedState.value = _musicSpeedState.value.copy(
            selectedArtist = artist,
            selectedSong = null  // Reset song when artist changes
        )
    }

    fun selectMusicSong(song: Song) {
        val state = _musicSpeedState.value

        // Load the song into the music player
        musicPlayer.loadSong(song)

        _musicSpeedState.value = state.copy(
            selectedSong = song,
            baseBpm = song.baseBpm,
            bpm = song.baseBpm,
            inSongSelect = false,  // Switch to play mode
            gameState = state.gameState.copy(
                phase = GamePhase.WAITING,
                gameDurationMs = song.durationMs
            )
        )
    }

    fun unlockMusicArtist(artist: MusicArtist) {
        val currentProgress = _musicSpeedProgress.value

        // Check if can afford
        if (currentProgress.totalPoints >= artist.unlockCost) {
            _musicSpeedProgress.value = currentProgress.copy(
                totalPoints = currentProgress.totalPoints - artist.unlockCost,
                unlockedArtistIds = currentProgress.unlockedArtistIds + artist.id
            )
        }
    }

    fun backToSongSelect() {
        _musicSpeedState.value = _musicSpeedState.value.copy(
            inSongSelect = true,
            gameState = _musicSpeedState.value.gameState.copy(phase = GamePhase.WAITING),
            songProgress = 0f,
            score = 0,
            pointsEarned = 0
        )
    }

    // ===== PAPER ROUTE =====
    private fun initializePaperRoute(difficulty: AIDifficulty) {
        _paperRouteState.value = PaperRouteState(
            gameState = GameState(phase = GamePhase.WAITING, gameDurationMs = 120_000),
            playerX = 0.5f,
            speed = 0f,
            papersLeft = 30,
            subscribers = 10,
            houses = generateInitialHouses(),
            obstacles = emptyList()
        )
    }

    private fun generateInitialHouses(): List<House> {
        val houses = mutableListOf<House>()
        var id = 0
        // Generate houses ABOVE the screen (negative y) so they scroll down into view
        // Houses scroll down as player moves forward
        for (i in 0 until 10) {
            val y = -0.1f - i * 0.2f  // Start above screen: -0.1, -0.3, -0.5, etc.
            // Left side house
            houses.add(
                House(
                    id = id++,
                    x = 0.05f,
                    y = y,
                    side = HouseSide.LEFT,
                    type = HouseType.entries.random(),
                    hasMailbox = true,
                    isSubscriber = Random.nextFloat() > 0.2f
                )
            )
            // Right side house (offset slightly)
            if (Random.nextFloat() > 0.3f) {
                houses.add(
                    House(
                        id = id++,
                        x = 0.85f,
                        y = y - 0.1f,
                        side = HouseSide.RIGHT,
                        type = HouseType.entries.random(),
                        hasMailbox = true,
                        isSubscriber = Random.nextFloat() > 0.2f
                    )
                )
            }
        }
        return houses
    }

    private fun startPaperRouteLoop() {
        lastFrameTime = System.currentTimeMillis()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaMs = currentTime - lastFrameTime
                lastFrameTime = currentTime
                updatePaperRoute(deltaMs)
                delay(frameDelayMs)
            }
        }
    }

    private fun updatePaperRoute(deltaMs: Long) {
        val state = _paperRouteState.value
        if (state.gameState.phase != GamePhase.PLAYING) return

        val newElapsedTime = state.gameState.elapsedTimeMs + deltaMs
        val deltaSeconds = deltaMs / 1000f

        val playerMetrics = bikeInputProcessor.lastMetrics
        val power = playerMetrics.power
        val resistance = playerMetrics.resistance

        // ===========================================
        // POWER CONTROLS FORWARD SPEED
        // ===========================================
        // 100W = slow crawl (0.3), 150W = cruising (0.5), 200W = brisk (0.7), 250W+ = rushing (1.0)
        // This creates a natural feel where more effort = faster delivery route
        val normalizedPower = when {
            power <= 50 -> 0.15f                          // Barely moving
            power <= 100 -> 0.15f + (power - 50) * 0.003f // 0.15 to 0.30
            power <= 150 -> 0.30f + (power - 100) * 0.004f // 0.30 to 0.50 (cruising zone)
            power <= 200 -> 0.50f + (power - 150) * 0.004f // 0.50 to 0.70 (brisk pace)
            power <= 250 -> 0.70f + (power - 200) * 0.006f // 0.70 to 1.00 (rushing)
            else -> 1.0f + (power - 250) * 0.002f          // 1.0+ for power riders
        }.coerceIn(0.1f, 1.3f)

        val speed = normalizedPower

        // ===========================================
        // PLAYER STAYS CENTERED - NO STEERING
        // ===========================================
        // Player bikes down the center of the street, focusing on throwing papers
        val newPlayerX = 0.5f  // Fixed center position

        // ===========================================
        // RESISTANCE CONTROLS THROW DIRECTION
        // ===========================================
        // Resistance UP = throw RIGHT toward houses on right side
        // Resistance DOWN = throw LEFT toward houses on left side
        // The magnitude of change determines throw power

        val resistanceChange = resistance - state.lastResistance
        var thrownPapers = state.thrownPapers.toMutableList()
        var papersLeft = state.papersLeft

        // Throw detection: need a clear resistance change (>8 points)
        // This prevents accidental throws while still being responsive
        val throwThreshold = 8
        val lastThrowTime = state.thrownPapers.maxOfOrNull { it.id.toLong() } ?: 0L
        val timeSinceLastThrow = System.currentTimeMillis() - lastThrowTime
        val canThrow = timeSinceLastThrow > 250 // 250ms cooldown between throws

        if (abs(resistanceChange) > throwThreshold && papersLeft > 0 && canThrow) {
            // Resistance UP = throw RIGHT, Resistance DOWN = throw LEFT
            val throwDirection = if (resistanceChange > 0) 1f else -1f

            // Throw power based on how quickly/strongly resistance changed
            val throwPower = (abs(resistanceChange) / 30f).coerceIn(0.5f, 1.5f)

            // Paper velocity - throws arc toward the houses
            // X velocity: direction toward left or right houses
            // Y velocity: papers fly forward (up the screen) and arc toward mailboxes
            val baseVelocityX = throwDirection * 1.2f * throwPower
            val baseVelocityY = -0.8f - speed * 0.3f  // Faster bike = paper goes further forward

            thrownPapers.add(
                ThrownPaper(
                    id = System.currentTimeMillis().toInt(),
                    x = newPlayerX,
                    y = state.playerY - 0.05f,
                    velocityX = baseVelocityX,
                    velocityY = baseVelocityY,
                    rotation = 0f
                )
            )
            papersLeft--
        }

        // ===========================================
        // UPDATE THROWN PAPERS WITH REALISTIC PHYSICS
        // ===========================================
        thrownPapers = thrownPapers.mapNotNull { paper ->
            // Apply gravity-like arc to paper trajectory
            val gravityEffect = 0.3f * deltaSeconds  // Paper arcs down slightly
            val newVelocityY = paper.velocityY + gravityEffect

            // Papers slow down horizontally due to air resistance
            val airResistance = 0.95f
            val newVelocityX = paper.velocityX * airResistance

            val newPaperX = paper.x + paper.velocityX * deltaSeconds
            val newPaperY = paper.y + paper.velocityY * deltaSeconds
            val newRotation = paper.rotation + 720f * deltaSeconds  // Paper spins

            // Remove papers that are off screen or have fallen past the player
            if (newPaperY < -0.2f || newPaperY > state.playerY + 0.1f ||
                newPaperX < -0.15f || newPaperX > 1.15f) {
                null
            } else {
                paper.copy(
                    x = newPaperX,
                    y = newPaperY,
                    velocityX = newVelocityX,
                    velocityY = newVelocityY,
                    rotation = newRotation
                )
            }
        }.toMutableList()

        // ===========================================
        // UPDATE DISTANCE AND SCROLL WORLD
        // ===========================================
        val distanceIncrement = speed * 15f * deltaSeconds  // meters per second
        val newDistance = state.distanceTraveled + distanceIncrement

        // Scroll houses down based on speed (faster = houses approach quicker)
        val scrollSpeed = speed * 0.6f * deltaSeconds
        var houses = state.houses.map { house ->
            house.copy(y = house.y + scrollSpeed)
        }.toMutableList()

        // ===========================================
        // COLLISION DETECTION - PAPER TO MAILBOX
        // ===========================================
        var score = state.score
        var papersDelivered = state.papersDelivered
        var papersMissed = state.papersMissed
        var streak = state.streak
        var maxStreak = state.maxStreak
        var subscribers = state.subscribers
        var combo = state.combo

        for (paper in thrownPapers.toList()) {
            for (i in houses.indices) {
                val house = houses[i]
                if (house.delivered) continue

                // Mailbox positions are at the edge of properties facing the street
                // Left houses: mailbox at x=0.18, Right houses: mailbox at x=0.82
                val mailboxX = if (house.side == HouseSide.LEFT) 0.18f else 0.82f

                // Generous hit detection for satisfying gameplay
                val hitDistanceX = 0.10f  // Horizontal tolerance
                val hitDistanceY = 0.08f  // Vertical tolerance

                val hitMailbox = abs(paper.x - mailboxX) < hitDistanceX &&
                                 abs(paper.y - house.y) < hitDistanceY

                if (hitMailbox) {
                    // ===========================================
                    // SUCCESSFUL DELIVERY - SCORING
                    // ===========================================
                    houses[i] = house.copy(delivered = true)
                    thrownPapers.remove(paper)

                    // Base points from house type
                    var points = house.type.points

                    // Timing bonus: hitting houses further up the screen (earlier timing)
                    // gives bonus points for skillful anticipation
                    val timingBonus = if (house.y < 0.3f) {
                        50  // Perfect timing - threw early
                    } else if (house.y < 0.5f) {
                        25  // Good timing
                    } else {
                        0   // Late but still hit
                    }
                    points += timingBonus

                    // Speed bonus: delivering while rushing is harder
                    val speedBonus = if (speed > 0.8f) 30 else if (speed > 0.6f) 15 else 0
                    points += speedBonus

                    // Apply combo multiplier
                    points *= combo

                    score += points
                    papersDelivered++
                    streak++
                    maxStreak = max(maxStreak, streak)

                    // ===========================================
                    // STREAK BONUSES
                    // ===========================================
                    // Combo increases every 3 successful deliveries (max 5x)
                    if (streak % 3 == 0 && streak > 0) {
                        combo = min(combo + 1, 5)
                    }

                    // Milestone bonuses for streaks
                    when (streak) {
                        5 -> score += 100   // "Nice streak!"
                        10 -> score += 250  // "Hot hand!"
                        15 -> score += 500  // "On fire!"
                        20 -> score += 1000 // "Paper route legend!"
                    }
                    break
                }

                // Check if paper hit a window (bad!) - smaller hit area
                val windowX = house.x
                val hitWindow = abs(paper.x - windowX) < 0.06f && abs(paper.y - house.y) < 0.05f

                if (hitWindow && !hitMailbox) {
                    houses[i] = house.copy(broken = true)
                    thrownPapers.remove(paper)

                    // Penalty for breaking windows
                    subscribers = max(0, subscribers - 1)
                    score = max(0, score - 50)  // Point penalty
                    streak = 0
                    combo = 1
                    break
                }
            }
        }

        // ===========================================
        // CHECK FOR MISSED SUBSCRIBER HOUSES
        // ===========================================
        houses = houses.filter { house ->
            if (house.y > 1.1f && house.isSubscriber && !house.delivered) {
                // Missed a subscriber's house!
                papersMissed++
                subscribers = max(0, subscribers - 1)
                streak = 0  // Break streak
                combo = 1   // Reset combo
                false       // Remove from list
            } else {
                house.y <= 1.2f
            }
        }.toMutableList()

        // ===========================================
        // GENERATE NEW HOUSES AHEAD
        // ===========================================
        val minHouseY = houses.minOfOrNull { it.y } ?: 0f
        if (minHouseY > -0.8f) {  // Generate houses further ahead
            var newId = (houses.maxOfOrNull { it.id } ?: 0) + 1

            // Spacing based on difficulty and randomness
            val spacing = 0.18f + Random.nextFloat() * 0.08f
            val newY = minHouseY - spacing

            // Always add a left-side house
            houses.add(
                House(
                    id = newId++,
                    x = 0.08f,
                    y = newY,
                    side = HouseSide.LEFT,
                    type = HouseType.entries.random(),
                    hasMailbox = true,
                    isSubscriber = Random.nextFloat() > 0.15f  // 85% are subscribers
                )
            )

            // 70% chance of a right-side house (slightly offset)
            if (Random.nextFloat() > 0.30f) {
                houses.add(
                    House(
                        id = newId,
                        x = 0.92f,
                        y = newY + Random.nextFloat() * 0.06f - 0.03f,  // Slight random offset
                        side = HouseSide.RIGHT,
                        type = HouseType.entries.random(),
                        hasMailbox = true,
                        isSubscriber = Random.nextFloat() > 0.15f
                    )
                )
            }
        }

        // ===========================================
        // OBSTACLES - SCROLL AND GENERATE
        // ===========================================
        var obstacles = state.obstacles.map { obs ->
            obs.copy(
                y = obs.y + scrollSpeed,
                x = obs.x + obs.velocityX * deltaSeconds
            )
        }.filter { it.y < 1.2f && it.y > -0.3f }.toMutableList()

        // Spawn obstacles occasionally (more at higher speeds)
        val obstacleChance = 0.008f + speed * 0.005f
        if (Random.nextFloat() < obstacleChance && obstacles.size < 4) {
            val obsType = StreetObstacle.entries.random()
            obstacles.add(
                StreetObstacleItem(
                    id = System.currentTimeMillis().toInt(),
                    x = Random.nextFloat() * 0.4f + 0.3f,  // Center of road
                    y = -0.15f,
                    type = obsType,
                    velocityX = when (obsType) {
                        StreetObstacle.SKATEBOARD_KID -> (Random.nextFloat() - 0.5f) * 0.4f
                        StreetObstacle.DOG -> (Random.nextFloat() - 0.5f) * 0.2f
                        else -> 0f
                    }
                )
            )
        }

        // ===========================================
        // CHECK GAME END CONDITIONS
        // ===========================================
        var newPhase = state.gameState.phase

        // Game ends if: time runs out, lose all subscribers, or out of papers
        if (newElapsedTime >= state.gameState.gameDurationMs) {
            // Time's up - add completion bonus based on performance
            val completionBonus = subscribers * 20 + papersDelivered * 5
            score += completionBonus
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        } else if (subscribers <= 0) {
            // Lost all subscribers - game over
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        } else if (papersLeft <= 0 && thrownPapers.isEmpty()) {
            // Out of papers and none in flight
            val outOfPapersBonus = if (papersDelivered > 20) 200 else 0
            score += outOfPapersBonus
            newPhase = GamePhase.FINISHED
            gameLoopJob?.cancel()
        }

        _paperRouteState.value = state.copy(
            gameState = state.gameState.copy(phase = newPhase, elapsedTimeMs = newElapsedTime),
            playerX = newPlayerX,
            speed = speed,
            distanceTraveled = newDistance,
            score = score,
            papersLeft = papersLeft,
            papersDelivered = papersDelivered,
            papersMissed = papersMissed,
            streak = streak,
            maxStreak = maxStreak,
            houses = houses,
            obstacles = obstacles,
            thrownPapers = thrownPapers,
            subscribers = subscribers,
            lastResistance = resistance,
            currentResistance = resistance,
            combo = combo
        )
    }


    fun updatePlayerMetrics(metrics: PlayerMetrics) {
        bikeInputProcessor.processMetrics(metrics)
    }

    fun pauseGame() {
        gameLoopJob?.cancel()

        when (currentGame) {
            ArcadeGame.SPRINT_RACE -> _sprintRaceState.value = _sprintRaceState.value.copy(
                gameState = _sprintRaceState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            ArcadeGame.POWER_WAR -> _powerWarState.value = _powerWarState.value.copy(
                gameState = _powerWarState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            ArcadeGame.RHYTHM_RIDE -> _rhythmRideState.value = _rhythmRideState.value.copy(
                gameState = _rhythmRideState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            ArcadeGame.ZOMBIE_ESCAPE -> _zombieEscapeState.value = _zombieEscapeState.value.copy(
                gameState = _zombieEscapeState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            ArcadeGame.HILL_CLIMB -> _hillClimbState.value = _hillClimbState.value.copy(
                gameState = _hillClimbState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            ArcadeGame.POWER_SURGE -> _powerSurgeState.value = _powerSurgeState.value.copy(
                gameState = _powerSurgeState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            ArcadeGame.MUSIC_SPEED -> {
                musicPlayer.pause()
                _musicSpeedState.value = _musicSpeedState.value.copy(
                    gameState = _musicSpeedState.value.gameState.copy(phase = GamePhase.PAUSED)
                )
            }
            ArcadeGame.PAPER_ROUTE -> _paperRouteState.value = _paperRouteState.value.copy(
                gameState = _paperRouteState.value.gameState.copy(phase = GamePhase.PAUSED)
            )
            null -> {}
        }
    }

    fun resumeGame() {
        when (currentGame) {
            ArcadeGame.SPRINT_RACE -> {
                _sprintRaceState.value = _sprintRaceState.value.copy(
                    gameState = _sprintRaceState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startSprintRaceLoop()
            }
            ArcadeGame.POWER_WAR -> {
                _powerWarState.value = _powerWarState.value.copy(
                    gameState = _powerWarState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startPowerWarLoop()
            }
            ArcadeGame.RHYTHM_RIDE -> {
                _rhythmRideState.value = _rhythmRideState.value.copy(
                    gameState = _rhythmRideState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startRhythmRideLoop()
            }
            ArcadeGame.ZOMBIE_ESCAPE -> {
                _zombieEscapeState.value = _zombieEscapeState.value.copy(
                    gameState = _zombieEscapeState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startZombieEscapeLoop()
            }
            ArcadeGame.HILL_CLIMB -> {
                _hillClimbState.value = _hillClimbState.value.copy(
                    gameState = _hillClimbState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startHillClimbLoop()
            }
            ArcadeGame.POWER_SURGE -> {
                _powerSurgeState.value = _powerSurgeState.value.copy(
                    gameState = _powerSurgeState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startPowerSurgeLoop()
            }
            ArcadeGame.MUSIC_SPEED -> {
                _musicSpeedState.value = _musicSpeedState.value.copy(
                    gameState = _musicSpeedState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startMusicSpeedLoop()
            }
            ArcadeGame.PAPER_ROUTE -> {
                _paperRouteState.value = _paperRouteState.value.copy(
                    gameState = _paperRouteState.value.gameState.copy(phase = GamePhase.PLAYING)
                )
                startPaperRouteLoop()
            }
            null -> {}
        }
    }

    fun endGame() {
        gameLoopJob?.cancel()
        countdownJob?.cancel()

        // Stop music for Music Speed game
        if (currentGame == ArcadeGame.MUSIC_SPEED) {
            musicPlayer.stop()
        }
    }

    fun getGameResult(): ArcadeGameResult? {
        return when (currentGame) {
            ArcadeGame.SPRINT_RACE -> {
                val state = _sprintRaceState.value
                val playerWon = state.aiPlayers.all { state.playerPosition > it.position }
                ArcadeGameResult(
                    game = ArcadeGame.SPRINT_RACE,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.score,
                    won = playerWon,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Distance" to "${state.distanceMeters.toInt()}m",
                        "Position" to if (playerWon) "1st" else "${state.aiPlayers.count { it.position > state.playerPosition } + 1}th"
                    )
                )
            }
            ArcadeGame.POWER_WAR -> {
                val state = _powerWarState.value
                ArcadeGameResult(
                    game = ArcadeGame.POWER_WAR,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.playerRoundsWon * 1000,
                    won = state.playerRoundsWon > state.aiRoundsWon,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Rounds Won" to "${state.playerRoundsWon} - ${state.aiRoundsWon}",
                        "Max Power" to "${state.playerPower}W"
                    )
                )
            }
            ArcadeGame.RHYTHM_RIDE -> {
                val state = _rhythmRideState.value
                val totalTargets = state.targetCadences.size
                val totalHits = state.perfectHits + state.goodHits
                val hitRate = if (totalTargets > 0) {
                    (totalHits * 100 / totalTargets)
                } else 0
                // Win condition: 70% hit rate on Easy/Medium, 60% on Hard/Extreme
                val winThreshold = when (currentDifficulty) {
                    AIDifficulty.EASY, AIDifficulty.MEDIUM -> 70
                    AIDifficulty.HARD -> 65
                    AIDifficulty.EXTREME -> 60
                }
                ArcadeGameResult(
                    game = ArcadeGame.RHYTHM_RIDE,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.score,
                    won = hitRate >= winThreshold,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Perfect" to state.perfectHits.toString(),
                        "Good" to state.goodHits.toString(),
                        "Miss" to state.misses.toString(),
                        "Max Combo" to state.maxCombo.toString(),
                        "Hit Rate" to "$hitRate%"
                    )
                )
            }
            ArcadeGame.ZOMBIE_ESCAPE -> {
                val state = _zombieEscapeState.value
                val survived = state.playerDistance > state.zombieDistance
                val survivalTime = state.gameState.elapsedTimeMs / 1000
                ArcadeGameResult(
                    game = ArcadeGame.ZOMBIE_ESCAPE,
                    durationSeconds = survivalTime.toInt(),
                    score = state.score,
                    won = survived && state.gameState.elapsedTimeMs >= state.gameState.gameDurationMs,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Distance" to "${state.playerDistance.toInt()}m",
                        "Close Calls" to state.closeCallCount.toString(),
                        "Survived" to if (survived) "Yes" else "Caught!"
                    )
                )
            }
            ArcadeGame.HILL_CLIMB -> {
                val state = _hillClimbState.value
                val reachedSummit = state.altitude >= state.targetAltitude
                val checkpointsReached = state.checkpoints.count { it.reached }
                ArcadeGameResult(
                    game = ArcadeGame.HILL_CLIMB,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.score,
                    won = reachedSummit,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Altitude" to "${state.altitude.toInt()}m",
                        "Summit" to "${state.targetAltitude.toInt()}m",
                        "Checkpoints" to "$checkpointsReached/4",
                        "Peak Grade" to "${state.grade.toInt()}%"
                    )
                )
            }
            ArcadeGame.POWER_SURGE -> {
                val state = _powerSurgeState.value
                val allDestroyed = state.targets.all { it.destroyed }
                val accuracy = if (state.shotsFired > 0) {
                    (state.shotsHit * 100 / state.shotsFired)
                } else 0
                ArcadeGameResult(
                    game = ArcadeGame.POWER_SURGE,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.score,
                    won = allDestroyed,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Targets" to "${state.targetsDestroyed}/${state.totalTargets}",
                        "Max Combo" to state.maxCombo.toString(),
                        "Accuracy" to "$accuracy%",
                        "Perfect Shots" to state.perfectShots.toString(),
                        "Multi-Hits" to state.multiHits.toString(),
                        "Cleared" to if (allDestroyed) "Yes!" else "No"
                    )
                )
            }
            ArcadeGame.MUSIC_SPEED -> {
                val state = _musicSpeedState.value
                val songCompleted = state.songProgress >= 1f
                ArcadeGameResult(
                    game = ArcadeGame.MUSIC_SPEED,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.score,
                    won = songCompleted,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Song" to (state.selectedSong?.title ?: "Unknown"),
                        "Max Speed" to "%.1fx".format(state.maxSpeedReached),
                        "Avg Speed" to "%.1fx".format(state.avgSpeed),
                        "Points Earned" to state.pointsEarned.toString()
                    )
                )
            }
            ArcadeGame.PAPER_ROUTE -> {
                val state = _paperRouteState.value
                val deliveryRate = if (state.papersDelivered + state.papersMissed > 0) {
                    state.papersDelivered.toFloat() / (state.papersDelivered + state.papersMissed)
                } else 0f
                ArcadeGameResult(
                    game = ArcadeGame.PAPER_ROUTE,
                    durationSeconds = (state.gameState.elapsedTimeMs / 1000).toInt(),
                    score = state.score,
                    won = state.subscribers > 0 && deliveryRate > 0.5f,
                    difficulty = currentDifficulty,
                    details = mapOf(
                        "Delivered" to "${state.papersDelivered}",
                        "Missed" to "${state.papersMissed}",
                        "Best Streak" to "${state.maxStreak}",
                        "Subscribers Left" to "${state.subscribers}"
                    )
                )
            }
            null -> null
        }
    }

    /**
     * Set the current user ID and wallet address for saving and syncing game results.
     * @param userId The local user ID
     * @param walletAddress Optional wallet address for backend sync
     * @param canSyncStats Whether stats sync is allowed (based on version check)
     */
    fun setUserId(userId: Long, walletAddress: String? = null, canSyncStats: Boolean = true) {
        currentUserId = userId
        currentWalletAddress = walletAddress
        statsSyncAllowed = canSyncStats
    }

    /**
     * Save the current game result to local database.
     * Returns true if saved successfully.
     */
    suspend fun saveGameResult(): Boolean {
        if (currentUserId == 0L) {
            Log.w(TAG, "Cannot save game result: no user ID set")
            return false
        }

        val result = getGameResult() ?: return false

        return try {
            val entity = ArcadeGameEntity.fromResult(currentUserId, result)
            arcadeGameDao.insertGame(entity)
            Log.d(TAG, "Saved arcade game result: ${result.game.name}, score=${result.score}, won=${result.won}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save arcade game result", e)
            false
        }
    }

    /**
     * Save game result and return the result for display.
     * Also triggers immediate sync to backend if wallet is connected and stats sync is allowed.
     * Stats sync may be disabled if the app version is too old (version check).
     */
    fun saveAndGetGameResult(): ArcadeGameResult? {
        val result = getGameResult()
        if (result != null && currentUserId != 0L) {
            viewModelScope.launch {
                val saved = saveGameResult()
                // Immediately sync to backend if wallet is connected AND version allows stats sync
                if (saved && currentWalletAddress != null) {
                    if (statsSyncAllowed) {
                        try {
                            val syncResult = historySyncManager.syncAllForUser(currentUserId, currentWalletAddress!!)
                            Log.d(TAG, "Immediate sync after game: ${syncResult.totalSynced} items synced")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync after game", e)
                        }
                    } else {
                        Log.d(TAG, "Stats sync skipped - app version too old. Stats saved locally only.")
                    }
                }
            }
        }
        return result
    }

    /**
     * Check if stats sync is blocked due to version
     */
    fun isStatsSyncBlocked(): Boolean {
        return !statsSyncAllowed && currentWalletAddress != null
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        countdownJob?.cancel()
        musicPlayer.release()
    }
}
