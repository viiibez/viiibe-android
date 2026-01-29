package com.viiibe.app.arcade.p2p

import kotlinx.coroutines.flow.StateFlow

/**
 * Connection states for the game transport layer
 */
enum class TransportConnectionState {
    IDLE,           // Not connected
    CONNECTING,     // Establishing connection
    CONNECTED,      // Connected and ready
    DISCONNECTED,   // Was connected, now disconnected
    ERROR           // Error state
}

/**
 * Interface for abstracting the transport layer of P2P games.
 *
 * All multiplayer games now use server-relayed transport for fair play.
 * Local P2P (Nearby Connections) has been removed to prevent cheating.
 */
interface GameTransport {

    /**
     * Current connection state as a StateFlow for reactive UI updates
     */
    val connectionState: StateFlow<TransportConnectionState>

    /**
     * Whether this transport is currently connected and ready to send/receive messages
     */
    val isConnected: Boolean

    /**
     * Connect to the transport layer (WebSocket server)
     */
    suspend fun connect()

    /**
     * Disconnect from the transport layer
     */
    suspend fun disconnect()

    /**
     * Send a P2P message to the opponent
     * @param message The message to send
     * @return True if the message was sent successfully
     */
    fun sendMessage(message: P2PMessage): Boolean

    /**
     * Set the listener for incoming messages
     * @param listener Callback invoked when a message is received
     */
    fun setMessageListener(listener: (P2PMessage) -> Unit)

    /**
     * Set the listener for connection state changes
     * @param listener Callback invoked when connection state changes
     */
    fun setConnectionStateListener(listener: (TransportConnectionState) -> Unit)

    /**
     * Clean up resources
     */
    fun destroy()
}

/**
 * Factory for creating GameTransport instances
 */
interface GameTransportFactory {
    /**
     * Create a transport for server-relayed games (Global matchmaking)
     */
    fun createServerTransport(serverUrl: String): GameTransport
}
