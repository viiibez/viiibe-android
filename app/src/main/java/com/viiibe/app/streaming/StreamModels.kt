package com.viiibe.app.streaming

import com.viiibe.app.arcade.data.ArcadeGame

enum class StreamConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class GameStreamPhase {
    WAITING,      // Game created, waiting for players
    STARTING,     // Countdown in progress
    LIVE,         // Game is being played
    ENDED,        // Game over, showing results
    SETTLED       // Payouts complete
}

data class GameStreamState(
    val gameId: String,
    val gameType: ArcadeGame,
    val phase: GameStreamPhase,
    val elapsedMs: Long,
    val durationMs: Long,
    val players: List<PlayerStreamState>,
    val spectatorCount: Int = 0,
    val bettingPool: BettingPoolState? = null
)

data class PlayerStreamState(
    val address: String,
    val name: String,
    val score: Int,
    val cadence: Int,
    val power: Int,
    val position: Float,        // Game-specific position (0-1 range)
    val isHost: Boolean
)

data class BettingPoolState(
    val poolA: Double,          // Total bet on player A (host)
    val poolB: Double,          // Total bet on player B (guest)
    val oddsA: Double,          // Calculated odds for player A
    val oddsB: Double,          // Calculated odds for player B
    val bettingClosed: Boolean  // True once game starts
)

// Outgoing messages (App -> Server)
sealed class StreamOutMessage {
    abstract val type: String
    abstract val gameId: String
    abstract val timestamp: Long

    data class RegisterGame(
        override val gameId: String,
        val gameType: String,
        val gameMode: String = "WAGERED",  // WAGERED or FRIENDLY
        val hostAddress: String,
        val hostName: String,
        val stakeAmount: Double,
        val durationSeconds: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "REGISTER_GAME"
    }

    data class PlayerJoined(
        override val gameId: String,
        val guestAddress: String,
        val guestName: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "PLAYER_JOINED"
    }

    data class GameStarting(
        override val gameId: String,
        val countdownSeconds: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "GAME_STARTING"
    }

    data class GameState(
        override val gameId: String,
        val elapsed: Long,
        val hostScore: Int,
        val hostCadence: Int,
        val hostPower: Int,
        val hostPosition: Float,
        val guestScore: Int,
        val guestCadence: Int,
        val guestPower: Int,
        val guestPosition: Float,
        val gameSpecific: Map<String, Any> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "GAME_STATE"
    }

    data class GameEnded(
        override val gameId: String,
        val hostFinalScore: Int,
        val guestFinalScore: Int,
        val winnerAddress: String?,
        val gameHash: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "GAME_ENDED"
    }

    data class GameSettled(
        override val gameId: String,
        val winnerAddress: String?,
        val settlementTxHash: String,
        val payoutAmount: Double,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "GAME_SETTLED"
    }

    data class Heartbeat(
        override val gameId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "HEARTBEAT"
    }

    data class GameCancelled(
        override val gameId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : StreamOutMessage() {
        override val type = "GAME_CANCELLED"
    }
}

// Incoming messages (Server -> App)
sealed class StreamInMessage {
    abstract val type: String

    data class SpectatorUpdate(
        val gameId: String,
        val spectatorCount: Int
    ) : StreamInMessage() {
        override val type = "SPECTATOR_UPDATE"
    }

    data class BettingUpdate(
        val gameId: String,
        val poolA: Double,
        val poolB: Double,
        val oddsA: Double,
        val oddsB: Double
    ) : StreamInMessage() {
        override val type = "BETTING_UPDATE"
    }

    data class ServerAck(
        val gameId: String,
        val messageType: String,
        val success: Boolean,
        val error: String? = null
    ) : StreamInMessage() {
        override val type = "ACK"
    }

    data class ServerError(
        val code: String,
        val message: String
    ) : StreamInMessage() {
        override val type = "ERROR"
    }
}
