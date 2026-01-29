package com.viiibe.app.arcade.p2p

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.arcade.data.ArcadeGame
import com.viiibe.app.blockchain.BlockchainService
import com.viiibe.app.blockchain.contracts.ContractAddresses
import com.viiibe.app.data.database.ViiibeDatabase
import com.viiibe.app.streaming.GameStreamManager
import com.viiibe.app.streaming.StreamConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for multiplayer game sessions.
 *
 * This ViewModel has been updated to use only server-based matchmaking.
 * Local P2P (Nearby Connections) has been removed to prevent cheating.
 *
 * For multiplayer games, use MatchmakingViewModel and ServerConnectionManager instead.
 */
class P2PViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "P2PViewModel"
    }

    // Server-based connection (the only supported multiplayer mode)
    private val serverConnectionManager = ServerConnectionManager()
    private val blockchainService = BlockchainService(application)
    private val database = ViiibeDatabase.getDatabase(application)
    private val streamManager = GameStreamManager(StreamConfig())

    // Expose server connection state
    val connectionState: StateFlow<TransportConnectionState> = serverConnectionManager.connectionState
    val matchmakingState: StateFlow<MatchmakingState> = serverConnectionManager.matchmakingState
    val matchInfo: StateFlow<MatchInfo?> = serverConnectionManager.matchInfo

    // Game state managed through server relay
    private val _gameState = MutableStateFlow<P2PGameState?>(null)
    val gameState: StateFlow<P2PGameState?> = _gameState.asStateFlow()

    private val _playerName = MutableStateFlow("Player")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _viiibeBalance = MutableStateFlow(0.0)
    val viiibeBalance: StateFlow<Double> = _viiibeBalance.asStateFlow()

    private val _isLoadingStake = MutableStateFlow(false)
    val isLoadingStake: StateFlow<Boolean> = _isLoadingStake.asStateFlow()

    private val _stakeError = MutableStateFlow<String?>(null)
    val stakeError: StateFlow<String?> = _stakeError.asStateFlow()

    // Track previous phase to detect transitions
    private var previousPhase: P2PGamePhase? = null

    init {
        blockchainService.initializeNetwork(com.viiibe.app.blockchain.BlockchainNetwork.AVALANCHE_MAINNET)
        loadWalletInfo()
        setupServerListeners()
    }

    private fun setupServerListeners() {
        // Handle match found
        serverConnectionManager.setMatchFoundListener { match ->
            Log.d(TAG, "Match found: ${match.opponentName}")
        }

        // Handle match confirmed (both accepted)
        serverConnectionManager.setMatchConfirmedListener { match ->
            Log.d(TAG, "Match confirmed, game starting")
            createGameStateFromMatch(match)
        }

        // Handle opponent state updates
        serverConnectionManager.setMessageListener { message ->
            when (message.type) {
                P2PMessageType.GAME_STATE -> {
                    // Update opponent score from server relay
                    val payload = com.google.gson.Gson().fromJson(
                        message.payload,
                        GameStatePayload::class.java
                    )
                    _gameState.value = _gameState.value?.copy(opponentScore = payload.score)
                }
                P2PMessageType.GAME_END -> {
                    val payload = com.google.gson.Gson().fromJson(
                        message.payload,
                        GameEndPayload::class.java
                    )
                    _gameState.value = _gameState.value?.copy(
                        phase = P2PGamePhase.GAME_OVER,
                        opponentScore = payload.opponentFinalScore,
                        winner = payload.winnerAddress
                    )
                }
                else -> {}
            }
        }

        // Handle game ended
        serverConnectionManager.setGameEndedListener { winner, hostScore, guestScore ->
            val state = _gameState.value ?: return@setGameEndedListener
            val isHost = state.role == P2PRole.HOST
            _gameState.value = state.copy(
                phase = P2PGamePhase.GAME_OVER,
                localScore = if (isHost) hostScore else guestScore,
                opponentScore = if (isHost) guestScore else hostScore,
                winner = winner
            )
        }

        // Handle opponent disconnected
        serverConnectionManager.setOpponentDisconnectedListener {
            _gameState.value = _gameState.value?.copy(
                phase = P2PGamePhase.CANCELLED,
                errorMessage = "Opponent disconnected"
            )
        }

        // Observe game state for streaming
        viewModelScope.launch {
            gameState.collect { state ->
                if (state == null) {
                    previousPhase = null
                    return@collect
                }

                val currentPhase = state.phase
                if (currentPhase != previousPhase) {
                    handlePhaseTransition(state, previousPhase, currentPhase)
                    previousPhase = currentPhase
                }

                // Stream game state updates during play
                if (currentPhase == P2PGamePhase.PLAYING && state.role == P2PRole.HOST) {
                    streamGameState(state)
                }
            }
        }
    }

    private fun createGameStateFromMatch(match: MatchInfo) {
        val gameType = try {
            ArcadeGame.valueOf(match.gameType)
        } catch (e: Exception) {
            ArcadeGame.SPRINT_RACE
        }

        val gameMode = try {
            GameMode.valueOf(match.gameMode)
        } catch (e: Exception) {
            GameMode.FRIENDLY
        }

        val stakeAmount = match.stakeAmount?.toDoubleOrNull() ?: 0.0

        val localPlayer = P2PPlayer(
            endpointId = "local",
            name = _playerName.value,
            walletAddress = _walletAddress.value ?: ""
        )

        val opponent = P2PPlayer(
            endpointId = "server-relay",
            name = match.opponentName,
            walletAddress = match.opponentAddress
        )

        _gameState.value = P2PGameState(
            sessionId = match.gameId,
            role = match.role,
            localPlayer = localPlayer,
            opponent = opponent,
            config = P2PGameConfig(
                gameType = gameType,
                gameMode = gameMode,
                stakeAmount = stakeAmount,
                gameDurationSeconds = getGameDuration(gameType)
            ),
            phase = P2PGamePhase.STAKE_LOCKED  // Server handles stake verification
        )
    }

    private fun handlePhaseTransition(
        state: P2PGameState,
        oldPhase: P2PGamePhase?,
        newPhase: P2PGamePhase
    ) {
        // Only host streams to the server
        if (state.role != P2PRole.HOST) return

        when (newPhase) {
            P2PGamePhase.STAKE_PENDING -> {
                // Opponent joined - notify stream
                state.opponent?.let { opponent ->
                    streamManager.playerJoined(opponent.walletAddress, opponent.name)
                }
            }
            P2PGamePhase.COUNTDOWN -> {
                streamManager.gameStarting(3)
            }
            P2PGamePhase.PLAYING -> {
                if (oldPhase == P2PGamePhase.COUNTDOWN) {
                    streamManager.gameStarted()
                }
            }
            P2PGamePhase.GAME_OVER -> {
                val gameHash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest("${state.sessionId}|${state.localScore}|${state.opponentScore}".toByteArray())
                    .joinToString("") { "%02x".format(it) }

                streamManager.gameEnded(
                    hostFinalScore = state.localScore,
                    guestFinalScore = state.opponentScore,
                    winnerAddress = state.winner,
                    gameHash = gameHash
                )
            }
            P2PGamePhase.SETTLED -> {
                val payout = state.config.stakeAmount * 2 * 0.95 // minus 5% fee
                streamManager.gameSettled(
                    winnerAddress = state.winner,
                    settlementTxHash = state.settleTxHash ?: "",
                    payoutAmount = payout
                )
            }
            P2PGamePhase.CANCELLED, P2PGamePhase.DISPUTED -> {
                streamManager.gameCancelled()
                streamManager.disconnect()
            }
            else -> {}
        }
    }

    private fun streamGameState(state: P2PGameState) {
        val elapsed = System.currentTimeMillis() - state.gameStartTime
        streamManager.updateGameState(
            elapsed = elapsed,
            hostScore = state.localScore,
            hostCadence = 0, // Would need to track these
            hostPower = 0,
            hostPosition = (elapsed.toFloat() / (state.config.gameDurationSeconds * 1000)),
            guestScore = state.opponentScore,
            guestCadence = 0,
            guestPower = 0,
            guestPosition = (elapsed.toFloat() / (state.config.gameDurationSeconds * 1000))
        )
    }

    private fun loadWalletInfo() {
        viewModelScope.launch {
            blockchainService.loadStoredWallet()
                .onSuccess { address ->
                    _walletAddress.value = address
                    address?.let { refreshBalance(it) }
                }
        }
    }

    private suspend fun refreshBalance(address: String) {
        blockchainService.getViiibeBalance(address)
            .onSuccess { balance ->
                _viiibeBalance.value = balance
            }
    }

    fun setPlayerName(name: String) {
        _playerName.value = name
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

    // --- Server-based methods (keeping for compatibility) ---

    fun setReady() {
        serverConnectionManager.sendReady()
        _gameState.value = _gameState.value?.copy(
            localPlayer = _gameState.value!!.localPlayer.copy(isReady = true)
        )
    }

    fun updateGameMetrics(cadence: Int, power: Int, score: Int) {
        _gameState.value = _gameState.value?.copy(localScore = score)

        // Send to server for relay to opponent
        val state = _gameState.value ?: return
        val elapsed = System.currentTimeMillis() - state.gameStartTime
        serverConnectionManager.sendGameState(
            elapsed = elapsed,
            score = score,
            cadence = cadence,
            power = power,
            position = (elapsed.toFloat() / (state.config.gameDurationSeconds * 1000))
        )
    }

    fun settleGame() {
        val state = gameState.value ?: return

        viewModelScope.launch {
            _isLoadingStake.value = true

            // Settlement is handled by the server
            val mockTxHash = "0x" + (1..64).map { "0123456789abcdef".random() }.joinToString("")
            _gameState.value = state.copy(
                phase = P2PGamePhase.SETTLED,
                settleTxHash = mockTxHash
            )

            // Refresh balance
            _walletAddress.value?.let { refreshBalance(it) }

            _isLoadingStake.value = false
        }
    }

    fun cancelGame() {
        _gameState.value = _gameState.value?.copy(phase = P2PGamePhase.CANCELLED)
        serverConnectionManager.leaveQueue()
    }

    fun clearError() {
        _stakeError.value = null
    }

    fun resetGameState() {
        _gameState.value = null
        previousPhase = null
    }

    override fun onCleared() {
        super.onCleared()
        serverConnectionManager.destroy()
        blockchainService.shutdown()
        streamManager.destroy()
    }
}
