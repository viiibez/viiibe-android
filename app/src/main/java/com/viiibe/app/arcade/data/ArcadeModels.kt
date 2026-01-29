package com.viiibe.app.arcade.data

/**
 * Available arcade games
 */
enum class ArcadeGame(
    val displayName: String,
    val description: String,
    val minPlayers: Int,
    val maxPlayers: Int
) {
    SPRINT_RACE(
        displayName = "Sprint Race",
        description = "Race against AI opponents. Cadence controls speed, resistance changes to jump/slide over obstacles.",
        minPlayers = 1,
        maxPlayers = 4
    ),
    POWER_WAR(
        displayName = "Power War",
        description = "Tug of war battle! Your power pulls the rope. Out-power your opponent to win.",
        minPlayers = 1,
        maxPlayers = 2
    ),
    RHYTHM_RIDE(
        displayName = "Rhythm Ride",
        description = "Match target cadences to the beat! Smooth progressions with drum beats to guide you.",
        minPlayers = 1,
        maxPlayers = 1
    ),
    ZOMBIE_ESCAPE(
        displayName = "Zombie Escape",
        description = "Zombies are chasing you! Pedal fast to escape. Slow down and they catch up!",
        minPlayers = 1,
        maxPlayers = 1
    ),
    HILL_CLIMB(
        displayName = "Hill Climb",
        description = "Climb the mountain! Maintain steady cadence to make progress. Too slow = roll back!",
        minPlayers = 1,
        maxPlayers = 1
    ),
    POWER_SURGE(
        displayName = "Power Surge",
        description = "Charge your power meter, then release energy blasts to destroy targets!",
        minPlayers = 1,
        maxPlayers = 1
    ),
    MUSIC_SPEED(
        displayName = "Music Speed",
        description = "Pick a song and pedal! The faster you go, the faster the music plays. Earn points to unlock new artists!",
        minPlayers = 1,
        maxPlayers = 1
    ),
    PAPER_ROUTE(
        displayName = "Paper Route",
        description = "Deliver papers to houses! Steer with the resistance knob, throw with quick resistance changes. Don't miss a house!",
        minPlayers = 1,
        maxPlayers = 1
    )
}

/**
 * AI difficulty levels
 */
enum class AIDifficulty(
    val displayName: String,
    val cadenceRange: IntRange,
    val powerRange: IntRange,
    val reactionTimeMs: Long,
    val consistency: Float // 0-1, how consistent the AI maintains target metrics
) {
    EASY(
        displayName = "Easy",
        cadenceRange = 50..70,
        powerRange = 80..120,
        reactionTimeMs = 500,
        consistency = 0.6f
    ),
    MEDIUM(
        displayName = "Medium",
        cadenceRange = 65..85,
        powerRange = 120..180,
        reactionTimeMs = 300,
        consistency = 0.75f
    ),
    HARD(
        displayName = "Hard",
        cadenceRange = 75..95,
        powerRange = 160..240,
        reactionTimeMs = 150,
        consistency = 0.85f
    ),
    EXTREME(
        displayName = "Extreme",
        cadenceRange = 85..110,
        powerRange = 200..300,
        reactionTimeMs = 80,
        consistency = 0.95f
    )
}

/**
 * AI player personality affects behavior patterns
 */
enum class AIPersonality(val displayName: String) {
    STEADY("Steady Eddie"),      // Consistent pace, no bursts
    SPRINTER("Sprint Queen"),    // Bursts of speed, then recovers
    CLIMBER("Mountain Goat"),    // Strong on resistance, slower cadence
    BALANCED("All-Rounder"),     // Adapts to player
    AGGRESSIVE("The Aggressor") // Always pushes hard, may fatigue
}

/**
 * Represents an AI opponent
 */
data class AIPlayer(
    val id: Int,
    val name: String,
    val difficulty: AIDifficulty,
    val personality: AIPersonality,
    val avatarColorIndex: Int
) {
    companion object {
        private val aiNames = listOf(
            "Turbo Tom", "Swift Sally", "Power Pete", "Cadence Carl",
            "Resistance Rita", "Speedy Sam", "Watt Wendy", "RPM Rick",
            "Sprint Steve", "Enduro Emma", "Tempo Tina", "Burst Bob"
        )

        fun generate(
            id: Int,
            difficulty: AIDifficulty,
            personality: AIPersonality = AIPersonality.values().random()
        ): AIPlayer {
            return AIPlayer(
                id = id,
                name = aiNames[id % aiNames.size],
                difficulty = difficulty,
                personality = personality,
                avatarColorIndex = id % 15
            )
        }
    }
}

/**
 * Current metrics for a player (human or AI)
 */
data class PlayerMetrics(
    val cadence: Int = 0,
    val power: Int = 0,
    val resistance: Int = 0
)

/**
 * Game state shared across all games
 */
enum class GamePhase {
    WAITING,      // Waiting to start
    COUNTDOWN,    // 3-2-1-GO countdown
    PLAYING,      // Game in progress
    PAUSED,       // Game paused
    FINISHED      // Game over
}

/**
 * Base game state
 */
data class GameState(
    val phase: GamePhase = GamePhase.WAITING,
    val countdownValue: Int = 3,
    val elapsedTimeMs: Long = 0,
    val gameDurationMs: Long = 60_000 // Default 1 minute
)

/**
 * Bike input actions detected from metric changes
 */
sealed class BikeAction {
    object None : BikeAction()
    data class Pedaling(val cadence: Int) : BikeAction()
    data class PowerBurst(val watts: Int) : BikeAction()  // Sustained high power
    object ResistanceUp : BikeAction()   // Quick resistance increase (+10)
    object ResistanceDown : BikeAction() // Quick resistance decrease (-10)
}

/**
 * Sprint Race specific models
 */
data class SprintRaceState(
    val gameState: GameState = GameState(),
    val playerPosition: Float = 0f,        // 0-1 track progress
    val playerLane: Int = 1,               // 0-2 lanes
    val playerSpeed: Float = 0f,           // Current speed
    val hasBoost: Boolean = false,         // Boost active
    val boostCooldownMs: Long = 0,
    val aiPlayers: List<AIRacerState> = emptyList(),
    val obstacles: List<Obstacle> = emptyList(),
    val score: Int = 0,
    val distanceMeters: Float = 0f
)

data class AIRacerState(
    val player: AIPlayer,
    val position: Float = 0f,
    val lane: Int = 1,
    val speed: Float = 0f,
    val metrics: PlayerMetrics = PlayerMetrics()
)

data class Obstacle(
    val id: Int,
    val position: Float,    // Track position
    val lane: Int,          // Which lane (0-2)
    val type: ObstacleType,
    val passed: Boolean = false
)

enum class ObstacleType {
    HURDLE,     // Jump over (resistance up)
    PUDDLE,     // Slide under (resistance down)
    CONE        // Avoid (change lanes)
}

/**
 * Power War specific models
 */
data class PowerWarState(
    val gameState: GameState = GameState(gameDurationMs = 90_000), // 90 second rounds
    val ropePosition: Float = 0.5f,  // 0 = AI wins, 0.5 = center, 1 = player wins
    val playerPower: Int = 0,
    val aiPower: Int = 0,
    val playerFatigue: Float = 0f,   // 0-1 fatigue level
    val aiFatigue: Float = 0f,
    val playerAnchored: Boolean = false,
    val aiAnchored: Boolean = false,
    val round: Int = 1,
    val playerRoundsWon: Int = 0,
    val aiRoundsWon: Int = 0,
    val aiPlayer: AIPlayer? = null
)

/**
 * Rhythm Ride specific models
 *
 * A rhythm game where players match target cadences to the beat:
 * - Notes/beats scroll toward the player at a speed based on BPM
 * - Player must match their cadence to the target when notes arrive
 * - Perfect hit: within tight tolerance (difficulty-dependent, ~5 RPM)
 * - Good hit: within loose tolerance (difficulty-dependent, ~10 RPM)
 * - Miss: target passes without matching cadence
 * - Power multiplier rewards harder effort (higher watts = bonus points)
 * - Combo system builds with consecutive hits, resets on miss
 */
data class RhythmRideState(
    val gameState: GameState = GameState(gameDurationMs = 120_000), // 2 minute song
    val targetCadences: List<CadenceTarget> = emptyList(),
    val currentTargetIndex: Int = 0,
    val score: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val perfectHits: Int = 0,
    val goodHits: Int = 0,
    val misses: Int = 0,
    val bpm: Int = 120,                    // Song tempo
    val lastHitResult: HitResult? = null,  // For visual feedback display (PERFECT/GOOD/MISS popup)
    val lastHitTimeMs: Long = 0,           // When the last hit occurred (for fade-out timing)
    val powerMultiplier: Float = 1.0f,     // Current power bonus multiplier (1.0x-2.0x)
    val currentCadence: Int = 0            // Player's current cadence for display
)

data class CadenceTarget(
    val targetCadence: Int,
    val startTimeMs: Long,
    val durationMs: Long,
    val hit: HitResult = HitResult.PENDING
)

enum class HitResult {
    PENDING,
    PERFECT,  // Within +/- 5 RPM
    GOOD,     // Within +/- 10 RPM
    MISS      // Outside range
}

/**
 * Zombie Escape specific models
 */
data class ZombieEscapeState(
    val gameState: GameState = GameState(gameDurationMs = 120_000), // Survive 2 minutes
    val playerDistance: Float = 0f,          // How far you've traveled
    val zombieDistance: Float = -50f,        // Zombies start 50m behind
    val playerSpeed: Float = 0f,
    val zombieSpeed: Float = 15f,            // Base zombie speed
    val zombieCount: Int = 3,
    val heartbeatIntensity: Float = 0f,      // 0-1, increases as zombies get closer
    val powerUps: List<PowerUp> = emptyList(),
    val hasSpeedBoost: Boolean = false,
    val speedBoostTimeMs: Long = 0,
    val score: Int = 0,
    val closeCallCount: Int = 0              // Times zombies got very close
)

data class PowerUp(
    val id: Int,
    val position: Float,
    val type: PowerUpType,
    val collected: Boolean = false
)

enum class PowerUpType {
    SPEED_BOOST,    // Temporary speed increase
    ZOMBIE_SLOW,    // Slows zombies temporarily
    SCORE_BONUS     // Bonus points
}

/**
 * Hill Climb specific models
 */
data class HillClimbState(
    val gameState: GameState = GameState(gameDurationMs = 180_000), // 3 minutes
    val altitude: Float = 0f,                // Current height in meters
    val targetAltitude: Float = 1000f,       // Summit height
    val grade: Float = 5f,                   // Current hill grade %
    val speed: Float = 0f,
    val momentum: Float = 0f,                // Helps maintain speed
    val fatigueLevel: Float = 0f,
    val checkpoints: List<Checkpoint> = emptyList(),
    val nextCheckpoint: Int = 0,
    val score: Int = 0
)

data class Checkpoint(
    val altitude: Float,
    val name: String,
    val reached: Boolean = false
)

/**
 * Power Surge specific models
 *
 * A target-shooting game where power charges your shots:
 * - Power output charges an energy meter (200W = moderate, 300W+ = fast)
 * - Cadence controls vertical aim (high cadence = aim up, low = aim down)
 * - Resistance controls horizontal aim (low = left, high = right)
 * - When charged, release a blast by quickly dropping power (ease off pedaling)
 * - Higher charge level = bigger blast radius = hit multiple targets
 */
data class PowerSurgeState(
    val gameState: GameState = GameState(gameDurationMs = 90_000), // 90 seconds
    val chargeLevel: Float = 0f,             // 0-1 charge meter
    val targets: List<PowerTarget> = emptyList(),
    val score: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val blastReady: Boolean = false,
    val lastBlastTimeMs: Long = 0,
    val targetsDestroyed: Int = 0,
    val totalTargets: Int = 0,
    // Aiming system
    val aimX: Float = 0.5f,                  // 0-1 horizontal crosshair position (controlled by resistance)
    val aimY: Float = 0.5f,                  // 0-1 vertical crosshair position (controlled by cadence)
    // Visual feedback
    val blastInProgress: Boolean = false,    // True during blast animation
    val blastX: Float = 0.5f,                // Last blast position X
    val blastY: Float = 0.5f,                // Last blast position Y
    val blastRadius: Float = 0f,             // Size of the blast (based on charge level)
    val lastHitTargetIds: List<Int> = emptyList(),  // IDs of targets hit by last blast
    val chargeGlowIntensity: Float = 0f,     // 0-1 glow effect when charging (visual feedback)
    // Stats
    val totalDamageDealt: Int = 0,
    val shotsFired: Int = 0,
    val shotsHit: Int = 0,                   // Shots that hit at least one target
    val multiHits: Int = 0,                  // Shots that hit 2+ targets
    val perfectShots: Int = 0,               // Shots that hit target dead center
    // Power tracking for blast detection
    val lastPower: Int = 0,
    val peakChargePower: Int = 0             // Highest power during this charge cycle
)

data class PowerTarget(
    val id: Int,
    val x: Float,
    val y: Float,
    val size: TargetSize,
    val health: Int,
    val maxHealth: Int,
    val destroyed: Boolean = false,
    // Visual state
    val hitFlashTimeMs: Long = 0,            // Remaining time to show hit flash
    val shakeIntensity: Float = 0f,          // 0-1 shake when damaged
    val spawnTimeMs: Long = 0,               // When target appeared (for spawn animation)
    val isNewlySpawned: Boolean = false      // True for first 500ms after spawn
)

enum class TargetSize(val healthMultiplier: Int, val points: Int, val sizeMultiplier: Float) {
    SMALL(1, 100, 0.5f),
    MEDIUM(2, 200, 1f),
    LARGE(3, 300, 1.5f),
    BOSS(5, 500, 2f)
}

/**
 * Music Speed specific models
 */
data class MusicSpeedState(
    val gameState: GameState = GameState(gameDurationMs = 180_000), // 3 minutes per song
    val selectedSong: Song? = null,
    val selectedArtist: MusicArtist? = null,
    val playbackSpeed: Float = 1.0f,      // 0.5x to 2.0x based on cadence
    val currentCadence: Int = 0,
    val targetCadence: Int = 80,          // Base cadence for 1.0x speed
    val score: Int = 0,
    val pointsEarned: Int = 0,            // Points earned this session
    val totalPointsEarned: Int = 0,       // Lifetime points (for unlocks)
    val speedStreak: Int = 0,             // Consecutive seconds at high speed
    val maxSpeedReached: Float = 0f,
    val avgSpeed: Float = 0f,
    val inSongSelect: Boolean = true,     // True = selecting song, False = playing
    val songProgress: Float = 0f,         // 0-1 progress through song
    val bpm: Int = 120,                   // Current effective BPM
    val baseBpm: Int = 120,               // Song's base BPM
    val visualizerBars: List<Float> = List(20) { 0f }  // Audio visualizer levels
)

data class Song(
    val id: String,
    val title: String,
    val artistId: String,
    val durationMs: Long,
    val baseBpm: Int,
    val difficulty: SongDifficulty,
    val audioUrl: String? = null          // URL or resource ID for audio
)

enum class SongDifficulty(val displayName: String, val pointMultiplier: Float) {
    EASY("Easy", 1.0f),
    MEDIUM("Medium", 1.5f),
    HARD("Hard", 2.0f),
    EXTREME("Extreme", 3.0f)
}

data class MusicArtist(
    val id: String,
    val name: String,
    val genre: String,
    val imageUrl: String? = null,
    val unlockCost: Int,                  // Points needed to unlock (0 = free)
    val songs: List<Song>
)

/**
 * Player progress for Music Speed (persisted)
 */
data class MusicSpeedProgress(
    val totalPoints: Int = 0,
    val unlockedArtistIds: Set<String> = setOf("default"),  // Default artist always unlocked
    val highScores: Map<String, Int> = emptyMap(),          // songId -> highScore
    val totalSongsPlayed: Int = 0,
    val totalPlayTimeMs: Long = 0
)

/**
 * Pre-defined artists and songs
 */
object MusicLibrary {
    val artists = listOf(
        MusicArtist(
            id = "default",
            name = "Starter Pack",
            genre = "Various",
            unlockCost = 0,
            songs = listOf(
                Song("default_1", "Warm Up Beat", "default", 180_000, 100, SongDifficulty.EASY),
                Song("default_2", "Steady Ride", "default", 180_000, 110, SongDifficulty.EASY),
                Song("default_3", "Push It", "default", 180_000, 120, SongDifficulty.MEDIUM)
            )
        ),
        MusicArtist(
            id = "synthwave",
            name = "Synthwave Dreams",
            genre = "Synthwave",
            unlockCost = 500,
            songs = listOf(
                Song("synth_1", "Neon Nights", "synthwave", 180_000, 118, SongDifficulty.MEDIUM),
                Song("synth_2", "Retro Rush", "synthwave", 180_000, 126, SongDifficulty.MEDIUM),
                Song("synth_3", "Cyber Sprint", "synthwave", 180_000, 140, SongDifficulty.HARD)
            )
        ),
        MusicArtist(
            id = "edm",
            name = "EDM Power",
            genre = "Electronic",
            unlockCost = 750,
            songs = listOf(
                Song("edm_1", "Bass Drop", "edm", 180_000, 128, SongDifficulty.MEDIUM),
                Song("edm_2", "Festival Energy", "edm", 180_000, 138, SongDifficulty.HARD),
                Song("edm_3", "Rave Mode", "edm", 180_000, 150, SongDifficulty.EXTREME)
            )
        ),
        MusicArtist(
            id = "hiphop",
            name = "Hip Hop Hustle",
            genre = "Hip Hop",
            unlockCost = 600,
            songs = listOf(
                Song("hiphop_1", "Street Flow", "hiphop", 180_000, 95, SongDifficulty.EASY),
                Song("hiphop_2", "Grind Time", "hiphop", 180_000, 105, SongDifficulty.MEDIUM),
                Song("hiphop_3", "No Limits", "hiphop", 180_000, 115, SongDifficulty.MEDIUM)
            )
        ),
        MusicArtist(
            id = "rock",
            name = "Rock Legends",
            genre = "Rock",
            unlockCost = 1000,
            songs = listOf(
                Song("rock_1", "Highway Ride", "rock", 180_000, 120, SongDifficulty.MEDIUM),
                Song("rock_2", "Thunder Pedal", "rock", 180_000, 135, SongDifficulty.HARD),
                Song("rock_3", "Metal Machine", "rock", 180_000, 160, SongDifficulty.EXTREME)
            )
        ),
        MusicArtist(
            id = "latin",
            name = "Latin Fire",
            genre = "Latin",
            unlockCost = 800,
            songs = listOf(
                Song("latin_1", "Salsa Spin", "latin", 180_000, 100, SongDifficulty.EASY),
                Song("latin_2", "Reggaeton Ride", "latin", 180_000, 115, SongDifficulty.MEDIUM),
                Song("latin_3", "Fiesta Sprint", "latin", 180_000, 130, SongDifficulty.HARD)
            )
        )
    )

    fun getArtistById(id: String): MusicArtist? = artists.find { it.id == id }
    fun getSongById(songId: String): Song? = artists.flatMap { it.songs }.find { it.id == songId }
    fun getArtistForSong(songId: String): MusicArtist? = artists.find { artist ->
        artist.songs.any { it.id == songId }
    }
}

/**
 * Paper Route specific models
 */
data class PaperRouteState(
    val gameState: GameState = GameState(gameDurationMs = 120_000), // 2 minutes
    val playerX: Float = 0.5f,              // 0-1 horizontal position on road
    val playerY: Float = 0.8f,              // Fixed near bottom
    val speed: Float = 0f,                  // Current forward speed
    val distanceTraveled: Float = 0f,       // Total distance in meters
    val score: Int = 0,
    val papersLeft: Int = 30,               // Papers to throw
    val papersDelivered: Int = 0,
    val papersMissed: Int = 0,
    val streak: Int = 0,                    // Consecutive successful deliveries
    val maxStreak: Int = 0,
    val houses: List<House> = emptyList(),
    val obstacles: List<StreetObstacleItem> = emptyList(),
    val thrownPapers: List<ThrownPaper> = emptyList(),
    val subscribers: Int = 10,              // Lose subscribers for missed houses/broken windows
    val lastResistance: Int = 50,           // For detecting resistance changes
    val currentResistance: Int = 50,
    val streetSection: Int = 0,             // Current section of the route
    val combo: Int = 1                      // Score multiplier
)

data class House(
    val id: Int,
    val x: Float,                           // 0 = far left, 1 = far right
    val y: Float,                           // Scrolls down from top
    val side: HouseSide,
    val type: HouseType,
    val hasMailbox: Boolean = true,
    val delivered: Boolean = false,
    val broken: Boolean = false,            // Window broken = angry customer
    val isSubscriber: Boolean = true        // Non-subscribers don't need papers
)

enum class HouseSide { LEFT, RIGHT }

enum class HouseType(val points: Int, val color: Long) {
    NORMAL(100, 0xFF4CAF50),      // Green house
    FANCY(200, 0xFF2196F3),       // Blue mansion - worth more
    APARTMENT(150, 0xFFFF9800),   // Orange apartment - multiple deliveries
    GRUMPY(50, 0xFF9E9E9E)        // Gray house - grumpy customer, small target
}

data class StreetObstacleItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val type: StreetObstacle,
    val velocityX: Float = 0f,              // Moving obstacles
    val velocityY: Float = 0f
)

enum class StreetObstacle(val damage: Int, val size: Float) {
    CAR(0, 0.15f),               // Cars block the road, don't hit them
    DOG(1, 0.06f),               // Dogs chase you - lose a paper
    TRASH_CAN(0, 0.04f),         // Knock over = point bonus
    SKATEBOARD_KID(0, 0.05f),    // Weaves around
    PUDDLE(0, 0.08f),            // Slows you down
    CONSTRUCTION(0, 0.2f)        // Road block - must go around
}

data class ThrownPaper(
    val id: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float = 0f
)

/**
 * Game result for saving to history
 */
data class ArcadeGameResult(
    val game: ArcadeGame,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val score: Int,
    val won: Boolean,
    val difficulty: AIDifficulty,
    val details: Map<String, Any> = emptyMap() // Game-specific details
)
