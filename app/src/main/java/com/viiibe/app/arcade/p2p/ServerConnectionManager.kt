package com.viiibe.app.arcade.p2p

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Matchmaking states for global matchmaking flow
 */
enum class MatchmakingState {
    IDLE,           // Not in queue
    QUEUED,         // In matchmaking queue
    MATCH_FOUND,    // Match found, awaiting acceptance
    MATCH_ACCEPTED, // Both players accepted
    IN_GAME,        // Game in progress
    ERROR           // Error state
}

/**
 * Match information when a match is found
 */
data class MatchInfo(
    val gameId: String,
    val role: P2PRole,
    val opponentName: String,
    val opponentAddress: String,
    val gameType: String,
    val gameMode: String,
    val stakeAmount: String?,
    val acceptTimeoutMs: Long
)

/**
 * Server message types (from server to client)
 */
private object ServerMessageType {
    const val AUTH_SUCCESS = "AUTH_SUCCESS"
    const val AUTH_ERROR = "AUTH_ERROR"
    const val QUEUE_JOINED = "QUEUE_JOINED"
    const val QUEUE_LEFT = "QUEUE_LEFT"
    const val QUEUE_STATUS = "QUEUE_STATUS"
    const val QUEUE_ERROR = "QUEUE_ERROR"
    const val MATCH_FOUND = "MATCH_FOUND"
    const val MATCH_CONFIRMED = "MATCH_CONFIRMED"
    const val MATCH_CANCELLED = "MATCH_CANCELLED"
    const val GAME_STARTED = "GAME_STARTED"
    const val OPPONENT_STATE = "OPPONENT_STATE"
    const val OPPONENT_READY = "OPPONENT_READY"
    const val GAME_ENDED = "GAME_ENDED"
    const val GAME_SETTLED = "GAME_SETTLED"
    const val GAME_CANCELLED = "GAME_CANCELLED"
    const val OPPONENT_DISCONNECTED = "OPPONENT_DISCONNECTED"
    const val HEARTBEAT_ACK = "HEARTBEAT_ACK"
    const val ERROR = "ERROR"
}

/**
 * Client message types (from client to server)
 */
private object ClientMessageType {
    const val AUTHENTICATE = "AUTHENTICATE"
    const val JOIN_QUEUE = "JOIN_QUEUE"
    const val LEAVE_QUEUE = "LEAVE_QUEUE"
    const val MATCH_ACCEPTED = "MATCH_ACCEPTED"
    const val MATCH_DECLINED = "MATCH_DECLINED"
    const val READY = "READY"
    const val GAME_STATE = "GAME_STATE"
    const val SETTLEMENT_CONFIRM = "SETTLEMENT_CONFIRM"
    const val HEARTBEAT = "HEARTBEAT"
}

/**
 * Manages WebSocket connection to the Viiibe server for global matchmaking.
 *
 * This class handles:
 * - WebSocket connection lifecycle
 * - Player authentication
 * - Matchmaking queue management
 * - Game state relay during matches
 */
class ServerConnectionManager(
    private val serverUrl: String = "wss://viiibe-backend-production.up.railway.app/ws/player",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : GameTransport {

    companion object {
        private const val TAG = "ServerConnManager"
        private const val HEARTBEAT_INTERVAL_MS = 10000L
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = false

    // Connection state
    private val _connectionState = MutableStateFlow(TransportConnectionState.IDLE)
    override val connectionState: StateFlow<TransportConnectionState> = _connectionState.asStateFlow()

    override val isConnected: Boolean
        get() = _connectionState.value == TransportConnectionState.CONNECTED

    // Matchmaking state
    private val _matchmakingState = MutableStateFlow(MatchmakingState.IDLE)
    val matchmakingState: StateFlow<MatchmakingState> = _matchmakingState.asStateFlow()

    // Current match info
    private val _matchInfo = MutableStateFlow<MatchInfo?>(null)
    val matchInfo: StateFlow<MatchInfo?> = _matchInfo.asStateFlow()

    // Queue position
    private val _queuePosition = MutableStateFlow<Int?>(null)
    val queuePosition: StateFlow<Int?> = _queuePosition.asStateFlow()

    // Estimated wait time
    private val _estimatedWaitMs = MutableStateFlow<Long?>(null)
    val estimatedWaitMs: StateFlow<Long?> = _estimatedWaitMs.asStateFlow()

    // Authentication state
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // Game state from opponent
    private val _opponentState = MutableStateFlow<GameStatePayload?>(null)
    val opponentState: StateFlow<GameStatePayload?> = _opponentState.asStateFlow()

    // Listeners
    private var messageListener: ((P2PMessage) -> Unit)? = null
    private var connectionStateListener: ((TransportConnectionState) -> Unit)? = null
    private var matchFoundListener: ((MatchInfo) -> Unit)? = null
    private var matchConfirmedListener: ((MatchInfo) -> Unit)? = null
    private var matchCancelledListener: ((String) -> Unit)? = null
    private var opponentReadyListener: (() -> Unit)? = null
    private var gameEndedListener: ((String?, Int, Int) -> Unit)? = null
    private var opponentDisconnectedListener: (() -> Unit)? = null

    // Player info for authentication
    private var playerAddress: String? = null
    private var playerName: String? = null

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            reconnectAttempts = 0
            _connectionState.value = TransportConnectionState.CONNECTED
            connectionStateListener?.invoke(TransportConnectionState.CONNECTED)
            startHeartbeat()

            // Auto-authenticate if we have player info
            playerAddress?.let { address ->
                playerName?.let { name ->
                    authenticate(address, name)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code - $reason")
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            handleDisconnect()
            attemptReconnect()
        }
    }

    override suspend fun connect() {
        if (_connectionState.value == TransportConnectionState.CONNECTING ||
            _connectionState.value == TransportConnectionState.CONNECTED) {
            return
        }

        _connectionState.value = TransportConnectionState.CONNECTING
        connectionStateListener?.invoke(TransportConnectionState.CONNECTING)
        shouldReconnect = true

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, webSocketListener)
    }

    override suspend fun disconnect() {
        shouldReconnect = false
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = TransportConnectionState.DISCONNECTED
        _isAuthenticated.value = false
        _matchmakingState.value = MatchmakingState.IDLE
        _matchInfo.value = null
        _queuePosition.value = null
        _estimatedWaitMs.value = null
        _opponentState.value = null
        connectionStateListener?.invoke(TransportConnectionState.DISCONNECTED)
    }

    override fun sendMessage(message: P2PMessage): Boolean {
        if (!isConnected) {
            Log.w(TAG, "Cannot send message: not connected")
            return false
        }

        // Convert P2PMessage to server GAME_STATE format
        val json = when (message.type) {
            P2PMessageType.GAME_STATE -> {
                val payload = gson.fromJson(message.payload, GameStatePayload::class.java)
                gson.toJson(mapOf(
                    "type" to ClientMessageType.GAME_STATE,
                    "elapsed" to payload.elapsed,
                    "score" to payload.score,
                    "cadence" to payload.cadence,
                    "power" to payload.power,
                    "position" to payload.position
                ))
            }
            P2PMessageType.READY -> {
                gson.toJson(mapOf("type" to ClientMessageType.READY))
            }
            P2PMessageType.SETTLEMENT_CONFIRM -> {
                val payload = gson.fromJson(message.payload, WinnerDeclaredPayload::class.java)
                gson.toJson(mapOf(
                    "type" to ClientMessageType.SETTLEMENT_CONFIRM,
                    "gameId" to message.sessionId,
                    "txHash" to payload.settlementTxHash
                ))
            }
            else -> {
                // Send as generic P2P message
                gson.toJson(message)
            }
        }

        return webSocket?.send(json) ?: false
    }

    override fun setMessageListener(listener: (P2PMessage) -> Unit) {
        messageListener = listener
    }

    override fun setConnectionStateListener(listener: (TransportConnectionState) -> Unit) {
        connectionStateListener = listener
    }

    override fun destroy() {
        scope.launch { disconnect() }
        scope.cancel()
    }

    // --- Matchmaking API ---

    /**
     * Set player credentials for authentication
     */
    fun setPlayerInfo(address: String, name: String) {
        playerAddress = address
        playerName = name

        // If already connected, authenticate now
        if (isConnected) {
            authenticate(address, name)
        }
    }

    /**
     * Authenticate with the server
     */
    private fun authenticate(address: String, name: String) {
        val message = gson.toJson(mapOf(
            "type" to ClientMessageType.AUTHENTICATE,
            "address" to address,
            "name" to name
        ))
        webSocket?.send(message)
        Log.d(TAG, "Sent authentication request for $name")
    }

    /**
     * Join the matchmaking queue
     */
    fun joinQueue(gameType: String, gameMode: GameMode, stakeAmount: Double?) {
        // Auto-connect if not connected
        if (_connectionState.value != TransportConnectionState.CONNECTED) {
            Log.w(TAG, "Not connected, attempting to connect first...")
            scope.launch {
                connect()
                // Wait for connection and authentication
                var attempts = 0
                while (attempts < 30 && !_isAuthenticated.value) {
                    delay(100)
                    attempts++
                }
                if (_isAuthenticated.value) {
                    joinQueueInternal(gameType, gameMode, stakeAmount)
                } else {
                    Log.e(TAG, "Failed to authenticate after connecting")
                    _matchmakingState.value = MatchmakingState.ERROR
                }
            }
            return
        }

        if (!_isAuthenticated.value) {
            Log.w(TAG, "Connected but not authenticated, re-authenticating...")
            playerAddress?.let { address ->
                playerName?.let { name ->
                    authenticate(address, name)
                    // Try again after auth
                    scope.launch {
                        var attempts = 0
                        while (attempts < 30 && !_isAuthenticated.value) {
                            delay(100)
                            attempts++
                        }
                        if (_isAuthenticated.value) {
                            joinQueueInternal(gameType, gameMode, stakeAmount)
                        } else {
                            Log.e(TAG, "Failed to authenticate")
                            _matchmakingState.value = MatchmakingState.ERROR
                        }
                    }
                }
            }
            return
        }

        joinQueueInternal(gameType, gameMode, stakeAmount)
    }

    private fun joinQueueInternal(gameType: String, gameMode: GameMode, stakeAmount: Double?) {
        val message = mutableMapOf(
            "type" to ClientMessageType.JOIN_QUEUE,
            "gameType" to gameType,
            "gameMode" to gameMode.name
        )

        if (gameMode == GameMode.WAGERED && stakeAmount != null) {
            message["stakeAmount"] = stakeAmount.toString()
        }

        val json = gson.toJson(message)
        val sent = webSocket?.send(json) ?: false
        if (sent) {
            Log.d(TAG, "Joining queue for $gameType ($gameMode)")
        } else {
            Log.e(TAG, "Failed to send join queue message")
            _matchmakingState.value = MatchmakingState.ERROR
        }
    }

    /**
     * Leave the matchmaking queue
     */
    fun leaveQueue() {
        val message = gson.toJson(mapOf("type" to ClientMessageType.LEAVE_QUEUE))
        webSocket?.send(message)
        _matchmakingState.value = MatchmakingState.IDLE
        _queuePosition.value = null
        _estimatedWaitMs.value = null
        Log.d(TAG, "Left matchmaking queue")
    }

    /**
     * Accept a found match
     */
    fun acceptMatch(gameId: String) {
        val message = gson.toJson(mapOf(
            "type" to ClientMessageType.MATCH_ACCEPTED,
            "gameId" to gameId
        ))
        webSocket?.send(message)
        _matchmakingState.value = MatchmakingState.MATCH_ACCEPTED
        Log.d(TAG, "Accepted match $gameId")
    }

    /**
     * Decline a found match
     */
    fun declineMatch(gameId: String) {
        val message = gson.toJson(mapOf(
            "type" to ClientMessageType.MATCH_DECLINED,
            "gameId" to gameId
        ))
        webSocket?.send(message)
        _matchmakingState.value = MatchmakingState.IDLE
        _matchInfo.value = null
        Log.d(TAG, "Declined match $gameId")
    }

    /**
     * Send ready signal for game start
     */
    fun sendReady() {
        val message = gson.toJson(mapOf("type" to ClientMessageType.READY))
        webSocket?.send(message)
        Log.d(TAG, "Sent ready signal")
    }

    /**
     * Send game state update during play
     */
    fun sendGameState(elapsed: Long, score: Int, cadence: Int, power: Int, position: Float) {
        val message = gson.toJson(mapOf(
            "type" to ClientMessageType.GAME_STATE,
            "elapsed" to elapsed,
            "score" to score,
            "cadence" to cadence,
            "power" to power,
            "position" to position
        ))
        webSocket?.send(message)
    }

    // --- Listeners ---

    fun setMatchFoundListener(listener: (MatchInfo) -> Unit) {
        matchFoundListener = listener
    }

    fun setMatchConfirmedListener(listener: (MatchInfo) -> Unit) {
        matchConfirmedListener = listener
    }

    fun setMatchCancelledListener(listener: (String) -> Unit) {
        matchCancelledListener = listener
    }

    fun setOpponentReadyListener(listener: () -> Unit) {
        opponentReadyListener = listener
    }

    fun setGameEndedListener(listener: (winner: String?, hostScore: Int, guestScore: Int) -> Unit) {
        gameEndedListener = listener
    }

    fun setOpponentDisconnectedListener(listener: () -> Unit) {
        opponentDisconnectedListener = listener
    }

    // --- Message Handling ---

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            Log.d(TAG, "Received message: $type")

            when (type) {
                ServerMessageType.AUTH_SUCCESS -> handleAuthSuccess(json)
                ServerMessageType.AUTH_ERROR -> handleAuthError(json)
                ServerMessageType.QUEUE_JOINED -> handleQueueJoined(json)
                ServerMessageType.QUEUE_LEFT -> handleQueueLeft()
                ServerMessageType.QUEUE_ERROR -> handleQueueError(json)
                ServerMessageType.MATCH_FOUND -> handleMatchFound(json)
                ServerMessageType.MATCH_CONFIRMED -> handleMatchConfirmed(json)
                ServerMessageType.MATCH_CANCELLED -> handleMatchCancelled(json)
                ServerMessageType.GAME_STARTED -> handleGameStarted(json)
                ServerMessageType.OPPONENT_STATE -> handleOpponentState(json)
                ServerMessageType.OPPONENT_READY -> handleOpponentReady()
                ServerMessageType.GAME_ENDED -> handleGameEnded(json)
                ServerMessageType.GAME_SETTLED -> handleGameSettled(json)
                ServerMessageType.GAME_CANCELLED -> handleGameCancelled(json)
                ServerMessageType.OPPONENT_DISCONNECTED -> handleOpponentDisconnected()
                ServerMessageType.ERROR -> handleError(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }

    private fun handleAuthSuccess(json: JsonObject) {
        _isAuthenticated.value = true
        Log.d(TAG, "Authentication successful")
    }

    private fun handleAuthError(json: JsonObject) {
        val message = json.get("message")?.asString ?: "Unknown error"
        Log.e(TAG, "Authentication failed: $message")
        _connectionState.value = TransportConnectionState.ERROR
    }

    private fun handleQueueJoined(json: JsonObject) {
        _matchmakingState.value = MatchmakingState.QUEUED
        _queuePosition.value = json.get("position")?.asInt
        _estimatedWaitMs.value = json.get("estimatedWaitMs")?.asLong
        Log.d(TAG, "Joined queue at position ${_queuePosition.value}")
    }

    private fun handleQueueLeft() {
        _matchmakingState.value = MatchmakingState.IDLE
        _queuePosition.value = null
        _estimatedWaitMs.value = null
    }

    private fun handleQueueError(json: JsonObject) {
        val message = json.get("message")?.asString ?: "Unknown error"
        Log.e(TAG, "Queue error: $message")
        _matchmakingState.value = MatchmakingState.ERROR
    }

    private fun handleMatchFound(json: JsonObject) {
        val opponent = json.getAsJsonObject("opponent")

        val match = MatchInfo(
            gameId = json.get("gameId").asString,
            role = if (json.get("role").asString == "HOST") P2PRole.HOST else P2PRole.GUEST,
            opponentName = opponent.get("name").asString,
            opponentAddress = opponent.get("address").asString,
            gameType = json.get("gameType").asString,
            gameMode = json.get("gameMode").asString,
            stakeAmount = json.get("stakeAmount")?.asString,
            acceptTimeoutMs = json.get("acceptTimeoutMs")?.asLong ?: 30000
        )

        _matchInfo.value = match
        _matchmakingState.value = MatchmakingState.MATCH_FOUND

        matchFoundListener?.invoke(match)
        Log.d(TAG, "Match found: ${match.opponentName}")
    }

    private fun handleMatchConfirmed(json: JsonObject) {
        val match = _matchInfo.value ?: return
        _matchmakingState.value = MatchmakingState.IN_GAME

        matchConfirmedListener?.invoke(match)
        Log.d(TAG, "Match confirmed, game starting")
    }

    private fun handleMatchCancelled(json: JsonObject) {
        val reason = json.get("reason")?.asString ?: "Match cancelled"
        _matchInfo.value = null
        _matchmakingState.value = MatchmakingState.IDLE

        matchCancelledListener?.invoke(reason)
        Log.d(TAG, "Match cancelled: $reason")
    }

    private fun handleGameStarted(json: JsonObject) {
        _matchmakingState.value = MatchmakingState.IN_GAME
        Log.d(TAG, "Game started")

        // Convert to P2PMessage for compatibility
        val match = _matchInfo.value ?: return
        val message = P2PMessage(
            type = P2PMessageType.GAME_START,
            sessionId = match.gameId
        )
        messageListener?.invoke(message)
    }

    private fun handleOpponentState(json: JsonObject) {
        val stateJson = json.getAsJsonObject("state") ?: return

        val state = GameStatePayload(
            elapsed = stateJson.get("elapsed")?.asLong ?: 0,
            score = stateJson.get("score")?.asInt ?: 0,
            cadence = stateJson.get("cadence")?.asInt ?: 0,
            power = stateJson.get("power")?.asInt ?: 0,
            position = stateJson.get("position")?.asFloat ?: 0f
        )

        _opponentState.value = state

        // Convert to P2PMessage for compatibility
        val match = _matchInfo.value ?: return
        val message = P2PMessage(
            type = P2PMessageType.GAME_STATE,
            sessionId = match.gameId,
            payload = gson.toJson(state)
        )
        messageListener?.invoke(message)
    }

    private fun handleOpponentReady() {
        opponentReadyListener?.invoke()
        Log.d(TAG, "Opponent is ready")
    }

    private fun handleGameEnded(json: JsonObject) {
        val winner = json.get("winner")?.asString
        val hostScore = json.get("hostScore")?.asInt ?: 0
        val guestScore = json.get("guestScore")?.asInt ?: 0

        gameEndedListener?.invoke(winner, hostScore, guestScore)

        // Convert to P2PMessage
        val match = _matchInfo.value ?: return
        val payload = GameEndPayload(
            finalScore = if (match.role == P2PRole.HOST) hostScore else guestScore,
            opponentFinalScore = if (match.role == P2PRole.HOST) guestScore else hostScore,
            winnerAddress = winner,
            gameHash = ""
        )
        val message = P2PMessage(
            type = P2PMessageType.GAME_END,
            sessionId = match.gameId,
            payload = gson.toJson(payload)
        )
        messageListener?.invoke(message)

        Log.d(TAG, "Game ended. Winner: $winner")
    }

    private fun handleGameSettled(json: JsonObject) {
        val winner = json.get("winner")?.asString
        val txHash = json.get("settlementTxHash")?.asString

        val match = _matchInfo.value ?: return
        val payload = WinnerDeclaredPayload(
            winnerAddress = winner ?: "",
            settlementTxHash = txHash
        )
        val message = P2PMessage(
            type = P2PMessageType.SETTLEMENT_CONFIRM,
            sessionId = match.gameId,
            payload = gson.toJson(payload)
        )
        messageListener?.invoke(message)

        _matchmakingState.value = MatchmakingState.IDLE
        _matchInfo.value = null

        Log.d(TAG, "Game settled. Tx: $txHash")
    }

    private fun handleGameCancelled(json: JsonObject) {
        val reason = json.get("reason")?.asString ?: "Game cancelled"

        val match = _matchInfo.value
        if (match != null) {
            val message = P2PMessage(
                type = P2PMessageType.CANCEL,
                sessionId = match.gameId,
                payload = reason
            )
            messageListener?.invoke(message)
        }

        _matchmakingState.value = MatchmakingState.IDLE
        _matchInfo.value = null

        Log.d(TAG, "Game cancelled: $reason")
    }

    private fun handleOpponentDisconnected() {
        opponentDisconnectedListener?.invoke()
        Log.d(TAG, "Opponent disconnected")
    }

    private fun handleError(json: JsonObject) {
        val message = json.get("message")?.asString ?: "Unknown error"
        Log.e(TAG, "Server error: $message")
    }

    // --- Connection Management ---

    private fun handleDisconnect() {
        heartbeatJob?.cancel()
        _connectionState.value = TransportConnectionState.DISCONNECTED
        _isAuthenticated.value = false

        // Reset matchmaking state - server queue is cleared on disconnect
        val wasQueued = _matchmakingState.value == MatchmakingState.QUEUED
        _matchmakingState.value = MatchmakingState.IDLE
        _queuePosition.value = null
        _estimatedWaitMs.value = null

        // Keep match info if in game (opponent disconnect is handled separately)
        if (_matchmakingState.value != MatchmakingState.IN_GAME) {
            _matchInfo.value = null
        }

        connectionStateListener?.invoke(TransportConnectionState.DISCONNECTED)

        if (wasQueued) {
            Log.d(TAG, "Disconnected while in queue - queue position lost")
        }
    }

    private fun attemptReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = TransportConnectionState.ERROR
            connectionStateListener?.invoke(TransportConnectionState.ERROR)
            return
        }

        reconnectAttempts++
        Log.d(TAG, "Attempting reconnect ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        scope.launch {
            delay(RECONNECT_DELAY_MS * reconnectAttempts)
            if (shouldReconnect) {
                connect()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL_MS)
                val message = gson.toJson(mapOf("type" to ClientMessageType.HEARTBEAT))
                webSocket?.send(message)
            }
        }
    }
}
