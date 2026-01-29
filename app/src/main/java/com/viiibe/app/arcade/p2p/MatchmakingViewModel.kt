package com.viiibe.app.arcade.p2p

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.arcade.data.ArcadeGame
import com.viiibe.app.blockchain.BlockchainNetwork
import com.viiibe.app.blockchain.BlockchainService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for managing global matchmaking with server-relayed games.
 *
 * This replaces the discovery-based P2PViewModel flow with a queue-based
 * matchmaking system that connects players worldwide via the Viiibe server.
 */
class MatchmakingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MatchmakingVM"
        private const val SERVER_URL = "wss://viiibe-backend-production.up.railway.app/ws/player"
    }

    // Server connection manager for WebSocket communication
    val serverConnection = ServerConnectionManager(
        serverUrl = SERVER_URL,
        scope = viewModelScope
    )

    private val blockchainService = BlockchainService(application)

    // Player info
    private val _playerName = MutableStateFlow("Player")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _viiibeBalance = MutableStateFlow(0.0)
    val viiibeBalance: StateFlow<Double> = _viiibeBalance.asStateFlow()

    // Current game state for relay mode
    private val _currentMatch = MutableStateFlow<MatchInfo?>(null)
    val currentMatch: StateFlow<MatchInfo?> = _currentMatch.asStateFlow()

    // Game metrics during play
    private val _localScore = MutableStateFlow(0)
    val localScore: StateFlow<Int> = _localScore.asStateFlow()

    private val _opponentScore = MutableStateFlow(0)
    val opponentScore: StateFlow<Int> = _opponentScore.asStateFlow()

    private val _gameStartTime = MutableStateFlow<Long?>(null)
    val gameStartTime: StateFlow<Long?> = _gameStartTime.asStateFlow()

    // Errors
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        blockchainService.initializeNetwork(BlockchainNetwork.AVALANCHE_MAINNET)
        loadWalletInfo()
        setupListeners()
    }

    private fun loadWalletInfo() {
        viewModelScope.launch {
            blockchainService.loadStoredWallet()
                .onSuccess { address ->
                    _walletAddress.value = address
                    address?.let {
                        refreshBalance(it)
                        serverConnection.setPlayerInfo(it, _playerName.value)
                        // Auto-connect when wallet is available
                        try {
                            serverConnection.connect()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to auto-connect", e)
                        }
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load wallet", e)
                }
        }
    }

    private suspend fun refreshBalance(address: String) {
        blockchainService.getViiibeBalance(address)
            .onSuccess { balance ->
                _viiibeBalance.value = balance
            }
            .onFailure { e ->
                Log.e(TAG, "Failed to get balance", e)
            }
    }

    private fun setupListeners() {
        // Match confirmed - game is starting
        serverConnection.setMatchConfirmedListener { match ->
            _currentMatch.value = match
            _gameStartTime.value = System.currentTimeMillis()
            Log.d(TAG, "Match confirmed, starting game: ${match.gameId}")
        }

        // Match cancelled
        serverConnection.setMatchCancelledListener { reason ->
            _currentMatch.value = null
            _gameStartTime.value = null
            _error.value = reason
            Log.d(TAG, "Match cancelled: $reason")
        }

        // Opponent ready
        serverConnection.setOpponentReadyListener {
            Log.d(TAG, "Opponent is ready")
        }

        // Game ended
        serverConnection.setGameEndedListener { winner, hostScore, guestScore ->
            val match = _currentMatch.value ?: return@setGameEndedListener
            val isHost = match.role == P2PRole.HOST
            _localScore.value = if (isHost) hostScore else guestScore
            _opponentScore.value = if (isHost) guestScore else hostScore
            Log.d(TAG, "Game ended. Winner: $winner")
        }

        // Opponent disconnected
        serverConnection.setOpponentDisconnectedListener {
            _error.value = "Opponent disconnected"
            Log.d(TAG, "Opponent disconnected")
        }

        // Collect opponent state updates
        viewModelScope.launch {
            serverConnection.opponentState.collect { state ->
                state?.let {
                    _opponentScore.value = it.score
                }
            }
        }
    }

    // --- Public API ---

    fun setPlayerName(name: String) {
        _playerName.value = name
        _walletAddress.value?.let { address ->
            serverConnection.setPlayerInfo(address, name)
        }
    }

    /**
     * Connect to the matchmaking server
     */
    fun connect() {
        viewModelScope.launch {
            try {
                serverConnection.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                _error.value = "Failed to connect: ${e.message}"
            }
        }
    }

    /**
     * Disconnect from the matchmaking server
     */
    fun disconnect() {
        viewModelScope.launch {
            serverConnection.disconnect()
        }
    }

    /**
     * Join the matchmaking queue for a specific game type
     */
    fun joinQueue(gameType: ArcadeGame, gameMode: GameMode, stakeAmount: Double?) {
        val address = _walletAddress.value
        if (address == null) {
            _error.value = "Wallet not connected"
            return
        }

        // Validate stake amount for wagered games
        if (gameMode == GameMode.WAGERED) {
            val stake = stakeAmount ?: 0.0
            if (stake <= 0) {
                _error.value = "Stake amount must be greater than 0"
                return
            }
            if (stake > _viiibeBalance.value) {
                _error.value = "Insufficient VIIIBE balance"
                return
            }
        }

        serverConnection.joinQueue(
            gameType = gameType.name,
            gameMode = gameMode,
            stakeAmount = stakeAmount
        )
        Log.d(TAG, "Joining queue for ${gameType.name} (${gameMode.name})")
    }

    /**
     * Leave the matchmaking queue
     */
    fun leaveQueue() {
        serverConnection.leaveQueue()
    }

    /**
     * Accept a found match
     */
    fun acceptMatch(gameId: String) {
        serverConnection.acceptMatch(gameId)
    }

    /**
     * Decline a found match
     */
    fun declineMatch(gameId: String) {
        serverConnection.declineMatch(gameId)
    }

    /**
     * Signal ready to start the game
     */
    fun setReady() {
        serverConnection.sendReady()
    }

    /**
     * Update local game state during play
     * This sends the state to the server which relays it to the opponent
     */
    fun updateGameState(score: Int, cadence: Int, power: Int, position: Float) {
        val startTime = _gameStartTime.value ?: return
        val elapsed = System.currentTimeMillis() - startTime

        _localScore.value = score

        serverConnection.sendGameState(
            elapsed = elapsed,
            score = score,
            cadence = cadence,
            power = power,
            position = position
        )
    }

    /**
     * Confirm game settlement
     */
    fun confirmSettlement(txHash: String) {
        val match = _currentMatch.value ?: return

        val message = P2PMessage(
            type = P2PMessageType.SETTLEMENT_CONFIRM,
            sessionId = match.gameId,
            payload = """{"settlementTxHash":"$txHash","winnerAddress":""}"""
        )
        serverConnection.sendMessage(message)

        // Reset state
        _currentMatch.value = null
        _gameStartTime.value = null
        _localScore.value = 0
        _opponentScore.value = 0

        // Refresh balance after settlement
        viewModelScope.launch {
            _walletAddress.value?.let { refreshBalance(it) }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Reset all game state (e.g., when returning to lobby)
     */
    fun resetGameState() {
        _currentMatch.value = null
        _gameStartTime.value = null
        _localScore.value = 0
        _opponentScore.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            serverConnection.disconnect()
        }
        serverConnection.destroy()
        blockchainService.shutdown()
    }
}
