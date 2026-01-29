package com.viiibe.app.arcade.p2p

import com.viiibe.app.arcade.data.ArcadeGame

/**
 * Connection states for server-based matchmaking.
 *
 * Note: ADVERTISING and DISCOVERING have been removed as local P2P is no longer supported.
 */
enum class P2PConnectionState {
    IDLE,           // Not connected to server
    CONNECTING,     // Establishing connection to server
    CONNECTED,      // Connected to matchmaking server
    DISCONNECTED,   // Disconnected from server
    ERROR           // Connection error
}

enum class P2PGamePhase {
    LOBBY,          // Waiting for opponent
    STAKE_PENDING,  // Negotiating stake
    STAKE_LOCKED,   // Stakes approved on-chain
    COUNTDOWN,      // Pre-game countdown
    PLAYING,        // Game in progress
    GAME_OVER,      // Game ended, determining winner
    SETTLING,       // Settling on-chain
    SETTLED,        // Payout complete
    DISPUTED,       // Dispute raised
    CANCELLED       // Game cancelled
}

enum class P2PRole {
    HOST,
    GUEST
}

enum class GameMode {
    WAGERED,   // Traditional - players stake VIIIBE
    FRIENDLY   // No stakes - spectators bet, players earn fees
}

data class P2PPlayer(
    val endpointId: String,
    val name: String,
    val walletAddress: String,
    val isReady: Boolean = false,
    val hasApprovedStake: Boolean = false
)

data class P2PGameConfig(
    val gameType: ArcadeGame,
    val gameMode: GameMode = GameMode.WAGERED,  // WAGERED or FRIENDLY
    val stakeAmount: Double,          // Amount of $VIIIBE to wager (0 for friendly)
    val gameDurationSeconds: Int,
    val minCadence: Int = 40,         // Minimum cadence to be considered "active"
    val allowSpectators: Boolean = true
)

data class P2PGameState(
    val sessionId: String,
    val phase: P2PGamePhase = P2PGamePhase.LOBBY,
    val role: P2PRole,
    val localPlayer: P2PPlayer,
    val opponent: P2PPlayer? = null,
    val config: P2PGameConfig,
    val gameStartTime: Long = 0,
    val localScore: Int = 0,
    val opponentScore: Int = 0,
    val winner: String? = null,       // Wallet address of winner
    val stakeTxHash: String? = null,  // Transaction hash for stake lock
    val settleTxHash: String? = null, // Transaction hash for settlement
    val disputeDeadline: Long = 0,    // Timestamp when dispute window closes
    val errorMessage: String? = null
)

data class P2PMessage(
    val type: P2PMessageType,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: String = ""          // JSON-serialized payload
)

enum class P2PMessageType {
    // Connection
    PLAYER_INFO,          // Exchange player info (name, wallet)

    // Stake negotiation
    STAKE_PROPOSAL,       // Host proposes stake amount
    STAKE_ACCEPTED,       // Guest accepts stake
    STAKE_REJECTED,       // Guest rejects stake
    STAKE_LOCKED,         // Confirm on-chain stake lock

    // Game control
    READY,                // Player is ready to start
    COUNTDOWN_START,      // Begin countdown
    GAME_START,           // Game has started
    GAME_STATE,           // Periodic game state sync
    GAME_END,             // Game ended (with final scores)

    // Settlement
    WINNER_DECLARED,      // Declare winner
    SETTLEMENT_CONFIRM,   // Confirm settlement tx

    // Dispute
    DISPUTE_RAISE,        // Raise a dispute
    DISPUTE_EVIDENCE,     // Submit dispute evidence

    // Control
    PAUSE,                // Pause game
    RESUME,               // Resume game
    CANCEL,               // Cancel game/session
    HEARTBEAT,            // Keep-alive
    ERROR,                // Error message

    // Matchmaking (server-relay only)
    JOIN_QUEUE,           // Player joins matchmaking queue
    LEAVE_QUEUE,          // Player leaves queue
    QUEUE_STATUS,         // Server sends queue position/wait time
    MATCH_FOUND,          // Server found an opponent
    MATCH_ACCEPTED,       // Player accepts match
    MATCH_DECLINED,       // Player declines match
    MATCH_CONFIRMED,      // Both accepted, game starting
    OPPONENT_STATE,       // Relayed state from opponent
    OPPONENT_DISCONNECTED // Opponent disconnected
}

data class PlayerInfoPayload(
    val name: String,
    val walletAddress: String,
    val appVersion: String = "1.0.0"
)

data class StakeProposalPayload(
    val gameType: String,
    val stakeAmount: Double,
    val durationSeconds: Int
)

data class GameStatePayload(
    val elapsed: Long,
    val score: Int,
    val cadence: Int,
    val power: Int,
    val position: Float = 0f,        // Game-specific position (0-1)
    val gameSpecific: Map<String, Any> = emptyMap()
)

data class GameEndPayload(
    val finalScore: Int,
    val opponentFinalScore: Int,
    val winnerAddress: String?,
    val gameHash: String             // Hash of game data for verification
)

data class WinnerDeclaredPayload(
    val winnerAddress: String,
    val settlementTxHash: String?
)

// --- Matchmaking Payloads (for server-relay global matchmaking) ---

/**
 * Payload for joining the matchmaking queue
 */
data class JoinQueuePayload(
    val gameType: String,
    val gameMode: String,
    val stakeAmount: String? = null  // In wei for wagered games
)

/**
 * Payload for queue status updates from server
 */
data class QueueStatusPayload(
    val position: Int,
    val estimatedWaitMs: Long,
    val queueSize: Int
)

/**
 * Payload for match found notification
 */
data class MatchFoundPayload(
    val gameId: String,
    val role: String,  // "HOST" or "GUEST"
    val opponentName: String,
    val opponentAddress: String,
    val gameType: String,
    val gameMode: String,
    val stakeAmount: String?,
    val durationSeconds: Int,
    val acceptTimeoutMs: Long = 30000
)

/**
 * Payload for match response (accept/decline)
 */
data class MatchResponsePayload(
    val gameId: String,
    val accepted: Boolean
)

/**
 * Payload for opponent state relay
 */
data class OpponentStatePayload(
    val elapsed: Long,
    val score: Int,
    val cadence: Int,
    val power: Int,
    val position: Float
)

/**
 * Transport type for the game session.
 *
 * Note: Only SERVER_RELAY is supported. Local P2P has been removed to prevent cheating.
 */
enum class TransportType {
    SERVER_RELAY    // Server WebSocket relay (global) - only supported transport
}
