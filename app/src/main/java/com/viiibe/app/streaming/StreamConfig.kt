package com.viiibe.app.streaming

data class StreamConfig(
    val serverUrl: String = "wss://viiibe-backend-production.up.railway.app/ws/stream",
    val heartbeatIntervalMs: Long = 15000,
    val maxReconnectAttempts: Int = 5,
    val reconnectDelayMs: Long = 2000
)
