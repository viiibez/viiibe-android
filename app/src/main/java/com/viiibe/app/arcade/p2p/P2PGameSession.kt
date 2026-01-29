package com.viiibe.app.arcade.p2p

import android.util.Log
import com.google.gson.Gson
import com.viiibe.app.arcade.data.ArcadeGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Manages a multiplayer game session via server relay.
 *
 * This class has been updated to use ServerConnectionManager exclusively.
 * Local P2P (Nearby Connections) has been removed to prevent cheating.
 * All games are now server-verified for fair play.
 *
 * Note: For most use cases, prefer using MatchmakingViewModel directly.
 * This class is kept for backward compatibility.
 */
class P2PGameSession(
    private val serverConnection: ServerConnectionManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "P2PGameSession"
        private const val HEARTBEAT_INTERVAL_MS = 5000L
        private const val GAME_STATE_SYNC_INTERVAL_MS = 100L
        private const val DISPUTE_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val gson = Gson()

    private val _gameState = MutableStateFlow<P2PGameState?>(null)
    val gameState: StateFlow<P2PGameState?> = _gameState.asStateFlow()

    private var heartbeatJob: Job? = null
    private var gameSyncJob: Job? = null
    private var countdownJob: Job? = null

    private var localCadence: Int = 0
    private var localPower: Int = 0
    private var localScore: Int = 0

    init {
        setupMessageListener()
    }

    private fun setupMessageListener() {
        serverConnection.setMessageListener { message ->
            handleIncomingMessage(message)
        }
    }

    /**
     * Join the matchmaking queue to find an opponent
     */
    fun joinQueue(
        playerName: String,
        walletAddress: String,
        gameType: ArcadeGame,
        gameMode: GameMode = GameMode.WAGERED,
        stakeAmount: Double?
    ) {
        val localPlayer = P2PPlayer(
            endpointId = "local",
            name = playerName,
            walletAddress = walletAddress
        )
        val config = P2PGameConfig(
            gameType = gameType,
            gameMode = gameMode,
            stakeAmount = stakeAmount ?: 0.0,
            gameDurationSeconds = getGameDuration(gameType)
        )

        _gameState.value = P2PGameState(
            sessionId = "",  // Will be assigned by server
            role = P2PRole.HOST,  // Will be assigned by server
            localPlayer = localPlayer,
            config = config
        )

        serverConnection.setPlayerInfo(walletAddress, playerName)
        serverConnection.joinQueue(
            gameType = gameType.name,
            gameMode = gameMode,
            stakeAmount = stakeAmount
        )

        Log.d(TAG, "Joined queue for ${gameType.displayName} (${gameMode.name})")
    }

    fun leaveQueue() {
        serverConnection.leaveQueue()
        _gameState.value = null
    }

    fun acceptMatch(gameId: String) {
        serverConnection.acceptMatch(gameId)
    }

    fun declineMatch(gameId: String) {
        serverConnection.declineMatch(gameId)
    }

    fun acceptStake() {
        val state = _gameState.value ?: return
        sendMessage(P2PMessageType.STAKE_ACCEPTED, "")
        _gameState.value = state.copy(
            localPlayer = state.localPlayer.copy(hasApprovedStake = true)
        )
    }

    fun rejectStake() {
        sendMessage(P2PMessageType.STAKE_REJECTED, "")
        cancelSession()
    }

    fun confirmStakeLocked(txHash: String) {
        val state = _gameState.value ?: return
        sendMessage(P2PMessageType.STAKE_LOCKED, txHash)

        _gameState.value = state.copy(
            phase = P2PGamePhase.STAKE_LOCKED,
            stakeTxHash = txHash,
            localPlayer = state.localPlayer.copy(hasApprovedStake = true)
        )
    }

    fun setReady() {
        val state = _gameState.value ?: return
        sendMessage(P2PMessageType.READY, "")
        serverConnection.sendReady()

        _gameState.value = state.copy(
            localPlayer = state.localPlayer.copy(isReady = true)
        )

        // If host and both ready, start countdown
        if (state.role == P2PRole.HOST && state.opponent?.isReady == true) {
            startCountdown()
        }
    }

    private fun startCountdown() {
        sendMessage(P2PMessageType.COUNTDOWN_START, "")

        countdownJob = scope.launch {
            _gameState.value = _gameState.value?.copy(phase = P2PGamePhase.COUNTDOWN)

            // 3 second countdown
            delay(3000)

            startGame()
        }
    }

    private fun startGame() {
        val state = _gameState.value ?: return

        sendMessage(P2PMessageType.GAME_START, "")

        _gameState.value = state.copy(
            phase = P2PGamePhase.PLAYING,
            gameStartTime = System.currentTimeMillis()
        )

        startHeartbeat()
        startGameSync()

        Log.d(TAG, "Game started!")
    }

    fun updateLocalMetrics(cadence: Int, power: Int, score: Int) {
        localCadence = cadence
        localPower = power
        localScore = score
        _gameState.value = _gameState.value?.copy(localScore = score)
    }

    private fun startGameSync() {
        gameSyncJob = scope.launch {
            while (_gameState.value?.phase == P2PGamePhase.PLAYING) {
                val state = _gameState.value ?: break
                val elapsed = System.currentTimeMillis() - state.gameStartTime

                // Send game state to server for relay to opponent
                serverConnection.sendGameState(
                    elapsed = elapsed,
                    score = localScore,
                    cadence = localCadence,
                    power = localPower,
                    position = (elapsed.toFloat() / (state.config.gameDurationSeconds * 1000))
                )

                // Check if game should end
                if (elapsed >= state.config.gameDurationSeconds * 1000L) {
                    endGame()
                    break
                }

                delay(GAME_STATE_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun endGame() {
        gameSyncJob?.cancel()

        val state = _gameState.value ?: return
        val winner = determineWinner(state.localScore, state.opponentScore)

        val gameHash = hashGameData(
            state.sessionId,
            state.localPlayer.walletAddress,
            state.opponent?.walletAddress ?: "",
            state.localScore,
            state.opponentScore,
            state.gameStartTime
        )

        val payload = GameEndPayload(
            finalScore = state.localScore,
            opponentFinalScore = state.opponentScore,
            winnerAddress = winner,
            gameHash = gameHash
        )
        sendMessage(P2PMessageType.GAME_END, payload)

        _gameState.value = state.copy(
            phase = P2PGamePhase.GAME_OVER,
            winner = winner,
            disputeDeadline = System.currentTimeMillis() + DISPUTE_WINDOW_MS
        )

        Log.d(TAG, "Game ended. Winner: $winner")
    }

    private fun determineWinner(localScore: Int, opponentScore: Int): String? {
        val state = _gameState.value ?: return null
        return when {
            localScore > opponentScore -> state.localPlayer.walletAddress
            opponentScore > localScore -> state.opponent?.walletAddress
            else -> null // Tie
        }
    }

    fun confirmSettlement(txHash: String) {
        val state = _gameState.value ?: return

        val payload = WinnerDeclaredPayload(
            winnerAddress = state.winner ?: "",
            settlementTxHash = txHash
        )
        sendMessage(P2PMessageType.SETTLEMENT_CONFIRM, payload)

        _gameState.value = state.copy(
            phase = P2PGamePhase.SETTLED,
            settleTxHash = txHash
        )
    }

    fun raiseDispute(evidence: String) {
        val state = _gameState.value ?: return
        if (System.currentTimeMillis() > state.disputeDeadline) {
            Log.w(TAG, "Dispute window has closed")
            return
        }

        sendMessage(P2PMessageType.DISPUTE_RAISE, evidence)

        _gameState.value = state.copy(phase = P2PGamePhase.DISPUTED)
    }

    private fun handleIncomingMessage(message: P2PMessage) {
        val state = _gameState.value

        when (message.type) {
            P2PMessageType.PLAYER_INFO -> {
                val payload = gson.fromJson(message.payload, PlayerInfoPayload::class.java)
                val opponent = P2PPlayer(
                    endpointId = "server-relay",
                    name = payload.name,
                    walletAddress = payload.walletAddress
                )
                _gameState.value = state?.copy(
                    sessionId = message.sessionId.ifEmpty { state.sessionId },
                    opponent = opponent
                )
            }

            P2PMessageType.STAKE_PROPOSAL -> {
                val payload = gson.fromJson(message.payload, StakeProposalPayload::class.java)
                val gameType = try {
                    ArcadeGame.valueOf(payload.gameType)
                } catch (e: Exception) {
                    ArcadeGame.SPRINT_RACE
                }

                _gameState.value = state?.copy(
                    sessionId = message.sessionId,
                    phase = P2PGamePhase.STAKE_PENDING,
                    config = P2PGameConfig(
                        gameType = gameType,
                        stakeAmount = payload.stakeAmount,
                        gameDurationSeconds = payload.durationSeconds
                    )
                )
            }

            P2PMessageType.STAKE_ACCEPTED -> {
                _gameState.value = state?.copy(
                    opponent = state.opponent?.copy(hasApprovedStake = true)
                )
            }

            P2PMessageType.STAKE_REJECTED -> {
                _gameState.value = state?.copy(
                    phase = P2PGamePhase.CANCELLED,
                    errorMessage = "Opponent rejected the stake"
                )
            }

            P2PMessageType.STAKE_LOCKED -> {
                if (state?.stakeTxHash == null) {
                    _gameState.value = state?.copy(stakeTxHash = message.payload)
                }
                _gameState.value = _gameState.value?.copy(phase = P2PGamePhase.STAKE_LOCKED)
            }

            P2PMessageType.READY -> {
                _gameState.value = state?.copy(
                    opponent = state.opponent?.copy(isReady = true)
                )

                // If we're also ready and host, start countdown
                if (state?.role == P2PRole.HOST && state.localPlayer.isReady) {
                    startCountdown()
                }
            }

            P2PMessageType.COUNTDOWN_START -> {
                if (state?.role == P2PRole.GUEST) {
                    countdownJob = scope.launch {
                        _gameState.value = state.copy(phase = P2PGamePhase.COUNTDOWN)
                        delay(3000)
                        _gameState.value = _gameState.value?.copy(
                            phase = P2PGamePhase.PLAYING,
                            gameStartTime = System.currentTimeMillis()
                        )
                        startGameSync()
                    }
                }
            }

            P2PMessageType.GAME_START -> {
                if (state?.phase != P2PGamePhase.PLAYING) {
                    _gameState.value = state?.copy(
                        phase = P2PGamePhase.PLAYING,
                        gameStartTime = System.currentTimeMillis()
                    )
                }
            }

            P2PMessageType.GAME_STATE -> {
                val payload = gson.fromJson(message.payload, GameStatePayload::class.java)
                _gameState.value = state?.copy(opponentScore = payload.score)
            }

            P2PMessageType.GAME_END -> {
                gameSyncJob?.cancel()
                val payload = gson.fromJson(message.payload, GameEndPayload::class.java)
                _gameState.value = state?.copy(
                    phase = P2PGamePhase.GAME_OVER,
                    opponentScore = payload.finalScore,
                    winner = payload.winnerAddress,
                    disputeDeadline = System.currentTimeMillis() + DISPUTE_WINDOW_MS
                )
            }

            P2PMessageType.SETTLEMENT_CONFIRM -> {
                val payload = gson.fromJson(message.payload, WinnerDeclaredPayload::class.java)
                _gameState.value = state?.copy(
                    phase = P2PGamePhase.SETTLED,
                    settleTxHash = payload.settlementTxHash
                )
            }

            P2PMessageType.DISPUTE_RAISE -> {
                _gameState.value = state?.copy(phase = P2PGamePhase.DISPUTED)
            }

            P2PMessageType.CANCEL -> {
                _gameState.value = state?.copy(
                    phase = P2PGamePhase.CANCELLED,
                    errorMessage = "Session cancelled by opponent"
                )
                cleanup()
            }

            P2PMessageType.ERROR -> {
                _gameState.value = state?.copy(errorMessage = message.payload)
            }

            else -> Log.d(TAG, "Unhandled message type: ${message.type}")
        }
    }

    private fun sendMessage(type: P2PMessageType, payload: Any) {
        val state = _gameState.value ?: return
        val payloadJson = if (payload is String) payload else gson.toJson(payload)

        val message = P2PMessage(
            type = type,
            sessionId = state.sessionId,
            payload = payloadJson
        )
        serverConnection.sendMessage(message)
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (_gameState.value?.phase == P2PGamePhase.PLAYING) {
                sendMessage(P2PMessageType.HEARTBEAT, "")
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun cancelSession() {
        sendMessage(P2PMessageType.CANCEL, "")
        _gameState.value = _gameState.value?.copy(phase = P2PGamePhase.CANCELLED)
        cleanup()
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        gameSyncJob?.cancel()
        countdownJob?.cancel()
        serverConnection.leaveQueue()
    }

    fun destroy() {
        cleanup()
        _gameState.value = null
    }

    private fun getGameDuration(game: ArcadeGame): Int {
        return when (game) {
            ArcadeGame.SPRINT_RACE -> 90
            ArcadeGame.POWER_WAR -> 45
            ArcadeGame.RHYTHM_RIDE -> 120
            ArcadeGame.ZOMBIE_ESCAPE -> 120
            ArcadeGame.HILL_CLIMB -> 180
            ArcadeGame.POWER_SURGE -> 90
            ArcadeGame.MUSIC_SPEED -> 180
            ArcadeGame.PAPER_ROUTE -> 120
        }
    }

    private fun hashGameData(
        sessionId: String,
        player1: String,
        player2: String,
        score1: Int,
        score2: Int,
        startTime: Long
    ): String {
        val data = "$sessionId|$player1|$player2|$score1|$score2|$startTime"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
