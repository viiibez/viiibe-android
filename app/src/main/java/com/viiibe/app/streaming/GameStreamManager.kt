package com.viiibe.app.streaming

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.viiibe.app.arcade.data.ArcadeGame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class GameStreamManager(
    private val config: StreamConfig = StreamConfig()
) {
    companion object {
        private const val TAG = "GameStreamManager"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempts = 0

    private val _connectionState = MutableStateFlow(StreamConnectionState.DISCONNECTED)
    val connectionState: StateFlow<StreamConnectionState> = _connectionState.asStateFlow()

    private val _streamState = MutableStateFlow<GameStreamState?>(null)
    val streamState: StateFlow<GameStreamState?> = _streamState.asStateFlow()

    private val _spectatorCount = MutableStateFlow(0)
    val spectatorCount: StateFlow<Int> = _spectatorCount.asStateFlow()

    private val _bettingPool = MutableStateFlow<BettingPoolState?>(null)
    val bettingPool: StateFlow<BettingPoolState?> = _bettingPool.asStateFlow()

    private var currentGameId: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "✅ WebSocket CONNECTED to ${config.serverUrl}")
            Log.d(TAG, "Response: ${response.code} ${response.message}")
            _connectionState.value = StreamConnectionState.CONNECTED
            reconnectAttempts = 0
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received message: ${text.take(100)}...")
            handleIncomingMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket closing: $code - $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket closed: $code - $reason")
            handleDisconnection()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ WebSocket FAILURE: ${t.message}", t)
            Log.e(TAG, "Response: ${response?.code} ${response?.message}")
            _connectionState.value = StreamConnectionState.ERROR
            handleDisconnection()
        }
    }

    fun connect() {
        Log.d(TAG, "connect() called, current state: ${_connectionState.value}")

        if (_connectionState.value == StreamConnectionState.CONNECTED ||
            _connectionState.value == StreamConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected/connecting, skipping")
            return
        }

        _connectionState.value = StreamConnectionState.CONNECTING
        Log.d(TAG, "Attempting WebSocket connection to: ${config.serverUrl}")

        try {
            val request = Request.Builder()
                .url(config.serverUrl)
                .build()

            Log.d(TAG, "Created request, calling newWebSocket...")
            webSocket = client.newWebSocket(request, webSocketListener)
            Log.d(TAG, "newWebSocket called successfully, waiting for callbacks...")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during WebSocket connection: ${e.message}", e)
            _connectionState.value = StreamConnectionState.ERROR
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = StreamConnectionState.DISCONNECTED
        currentGameId = null
    }

    fun registerGame(
        gameId: String,
        gameType: ArcadeGame,
        gameMode: com.viiibe.app.arcade.p2p.GameMode = com.viiibe.app.arcade.p2p.GameMode.WAGERED,
        hostAddress: String,
        hostName: String,
        stakeAmount: Double,
        durationSeconds: Int
    ) {
        currentGameId = gameId

        _streamState.value = GameStreamState(
            gameId = gameId,
            gameType = gameType,
            phase = GameStreamPhase.WAITING,
            elapsedMs = 0,
            durationMs = durationSeconds * 1000L,
            players = listOf(
                PlayerStreamState(
                    address = hostAddress,
                    name = hostName,
                    score = 0,
                    cadence = 0,
                    power = 0,
                    position = 0f,
                    isHost = true
                )
            )
        )

        val message = StreamOutMessage.RegisterGame(
            gameId = gameId,
            gameType = gameType.name,
            gameMode = gameMode.name,
            hostAddress = hostAddress,
            hostName = hostName,
            stakeAmount = stakeAmount,
            durationSeconds = durationSeconds
        )

        sendMessage(message)
    }

    fun playerJoined(guestAddress: String, guestName: String) {
        val gameId = currentGameId ?: return
        val currentState = _streamState.value ?: return

        val updatedPlayers = currentState.players.toMutableList()
        updatedPlayers.add(
            PlayerStreamState(
                address = guestAddress,
                name = guestName,
                score = 0,
                cadence = 0,
                power = 0,
                position = 0f,
                isHost = false
            )
        )

        _streamState.value = currentState.copy(players = updatedPlayers)

        val message = StreamOutMessage.PlayerJoined(
            gameId = gameId,
            guestAddress = guestAddress,
            guestName = guestName
        )

        sendMessage(message)
    }

    fun gameStarting(countdownSeconds: Int = 3) {
        val gameId = currentGameId ?: return
        val currentState = _streamState.value ?: return

        _streamState.value = currentState.copy(phase = GameStreamPhase.STARTING)

        val message = StreamOutMessage.GameStarting(
            gameId = gameId,
            countdownSeconds = countdownSeconds
        )

        sendMessage(message)
    }

    fun gameStarted() {
        val currentState = _streamState.value ?: return
        _streamState.value = currentState.copy(
            phase = GameStreamPhase.LIVE,
            bettingPool = currentState.bettingPool?.copy(bettingClosed = true)
        )
    }

    fun updateGameState(
        elapsed: Long,
        hostScore: Int,
        hostCadence: Int,
        hostPower: Int,
        hostPosition: Float,
        guestScore: Int,
        guestCadence: Int,
        guestPower: Int,
        guestPosition: Float,
        gameSpecific: Map<String, Any> = emptyMap()
    ) {
        val gameId = currentGameId ?: return
        val currentState = _streamState.value ?: return

        // Update local state
        val updatedPlayers = currentState.players.map { player ->
            if (player.isHost) {
                player.copy(
                    score = hostScore,
                    cadence = hostCadence,
                    power = hostPower,
                    position = hostPosition
                )
            } else {
                player.copy(
                    score = guestScore,
                    cadence = guestCadence,
                    power = guestPower,
                    position = guestPosition
                )
            }
        }

        _streamState.value = currentState.copy(
            elapsedMs = elapsed,
            players = updatedPlayers
        )

        // Send to server
        val message = StreamOutMessage.GameState(
            gameId = gameId,
            elapsed = elapsed,
            hostScore = hostScore,
            hostCadence = hostCadence,
            hostPower = hostPower,
            hostPosition = hostPosition,
            guestScore = guestScore,
            guestCadence = guestCadence,
            guestPower = guestPower,
            guestPosition = guestPosition,
            gameSpecific = gameSpecific
        )

        sendMessage(message)
    }

    fun gameEnded(
        hostFinalScore: Int,
        guestFinalScore: Int,
        winnerAddress: String?,
        gameHash: String
    ) {
        val gameId = currentGameId ?: return
        val currentState = _streamState.value ?: return

        _streamState.value = currentState.copy(phase = GameStreamPhase.ENDED)

        val message = StreamOutMessage.GameEnded(
            gameId = gameId,
            hostFinalScore = hostFinalScore,
            guestFinalScore = guestFinalScore,
            winnerAddress = winnerAddress,
            gameHash = gameHash
        )

        sendMessage(message)
    }

    fun gameSettled(
        winnerAddress: String?,
        settlementTxHash: String,
        payoutAmount: Double
    ) {
        val gameId = currentGameId ?: return
        val currentState = _streamState.value ?: return

        _streamState.value = currentState.copy(phase = GameStreamPhase.SETTLED)

        val message = StreamOutMessage.GameSettled(
            gameId = gameId,
            winnerAddress = winnerAddress,
            settlementTxHash = settlementTxHash,
            payoutAmount = payoutAmount
        )

        sendMessage(message)
    }

    fun gameCancelled() {
        val gameId = currentGameId ?: return
        Log.d(TAG, "Sending game cancelled for $gameId")

        val message = StreamOutMessage.GameCancelled(gameId = gameId)
        sendMessage(message)

        // Clear local state
        _streamState.value = null
        currentGameId = null
    }

    private fun sendMessage(message: StreamOutMessage) {
        if (_connectionState.value != StreamConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send message, not connected")
            return
        }

        val json = gson.toJson(message)
        webSocket?.send(json)
        Log.d(TAG, "Sent: ${message.type}")
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val jsonObject = JsonParser.parseString(text).asJsonObject
            val type = jsonObject.get("type")?.asString ?: return

            when (type) {
                "SPECTATOR_UPDATE" -> {
                    val message = gson.fromJson(text, StreamInMessage.SpectatorUpdate::class.java)
                    _spectatorCount.value = message.spectatorCount

                    _streamState.value = _streamState.value?.copy(
                        spectatorCount = message.spectatorCount
                    )
                }

                "BETTING_UPDATE" -> {
                    val message = gson.fromJson(text, StreamInMessage.BettingUpdate::class.java)
                    val pool = BettingPoolState(
                        poolA = message.poolA,
                        poolB = message.poolB,
                        oddsA = message.oddsA,
                        oddsB = message.oddsB,
                        bettingClosed = _streamState.value?.phase == GameStreamPhase.LIVE
                    )
                    _bettingPool.value = pool
                    _streamState.value = _streamState.value?.copy(bettingPool = pool)
                }

                "ACK" -> {
                    val message = gson.fromJson(text, StreamInMessage.ServerAck::class.java)
                    if (!message.success) {
                        Log.w(TAG, "Server NACK for ${message.messageType}: ${message.error}")
                    }
                }

                "ERROR" -> {
                    val message = gson.fromJson(text, StreamInMessage.ServerError::class.java)
                    Log.e(TAG, "Server error: ${message.code} - ${message.message}")
                }

                else -> {
                    Log.d(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming message", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == StreamConnectionState.CONNECTED) {
                delay(config.heartbeatIntervalMs)
                currentGameId?.let { gameId ->
                    sendMessage(StreamOutMessage.Heartbeat(gameId))
                }
            }
        }
    }

    private fun handleDisconnection() {
        heartbeatJob?.cancel()
        webSocket = null

        if (reconnectAttempts < config.maxReconnectAttempts) {
            _connectionState.value = StreamConnectionState.RECONNECTING
            scope.launch {
                delay(config.reconnectDelayMs)
                reconnectAttempts++
                Log.d(TAG, "Attempting reconnect ($reconnectAttempts/${config.maxReconnectAttempts})")
                connect()
            }
        } else {
            _connectionState.value = StreamConnectionState.DISCONNECTED
            Log.w(TAG, "Max reconnect attempts reached")
        }
    }

    fun destroy() {
        scope.cancel()
        disconnect()
        client.dispatcher.executorService.shutdown()
    }
}
