package com.viiibe.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.data.database.ViiibeDatabase
import com.viiibe.app.data.model.CachedGameHistory
import com.viiibe.app.data.model.PlayerProfile
import com.viiibe.app.data.repository.PlayerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for managing player profile and game stats.
 * Handles syncing with backend, caching, and UI state.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val database = ViiibeDatabase.getDatabase(application)
    private val playerRepository = PlayerRepository(application, database.playerDao())

    // Current user context
    private val _currentUserId = MutableStateFlow(0L)
    private val _currentWalletAddress = MutableStateFlow<String?>(null)

    // Profile state
    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Initial)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    // Game history state
    private val _gameHistoryState = MutableStateFlow<GameHistoryState>(GameHistoryState.Initial)
    val gameHistoryState: StateFlow<GameHistoryState> = _gameHistoryState.asStateFlow()

    // Mini stats for home screen
    private val _miniStats = MutableStateFlow<PlayerRepository.MiniStats?>(null)
    val miniStats: StateFlow<PlayerRepository.MiniStats?> = _miniStats.asStateFlow()

    // Loading states
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Profile UI state
     */
    sealed class ProfileState {
        object Initial : ProfileState()
        object Loading : ProfileState()
        data class Success(val profile: PlayerProfile) : ProfileState()
        data class Error(val message: String, val cachedProfile: PlayerProfile? = null) : ProfileState()
    }

    /**
     * Game history UI state
     */
    sealed class GameHistoryState {
        object Initial : GameHistoryState()
        object Loading : GameHistoryState()
        data class Success(val games: List<CachedGameHistory>) : GameHistoryState()
        data class Error(val message: String, val cachedGames: List<CachedGameHistory> = emptyList()) : GameHistoryState()
    }

    /**
     * Set the current user and wallet address
     */
    fun setCurrentUser(userId: Long, walletAddress: String?) {
        _currentUserId.value = userId
        _currentWalletAddress.value = walletAddress

        if (walletAddress != null) {
            // Start observing mini stats
            observeMiniStats(userId)
        }
    }

    /**
     * Observe mini stats from cache
     */
    private fun observeMiniStats(userId: Long) {
        viewModelScope.launch {
            playerRepository.getMiniStats(userId).collect { stats ->
                _miniStats.value = stats
            }
        }
    }

    /**
     * Load player profile
     */
    fun loadProfile(forceRefresh: Boolean = false) {
        val userId = _currentUserId.value
        val walletAddress = _currentWalletAddress.value

        if (userId <= 0 || walletAddress == null) {
            _profileState.value = ProfileState.Error("No wallet connected")
            return
        }

        viewModelScope.launch {
            playerRepository.getPlayerProfile(walletAddress, userId, forceRefresh)
                .collect { result ->
                    _profileState.value = when (result) {
                        is PlayerRepository.SyncResult.Loading -> ProfileState.Loading
                        is PlayerRepository.SyncResult.Success -> ProfileState.Success(result.data)
                        is PlayerRepository.SyncResult.Error -> {
                            val cached = result.cachedData as? PlayerProfile
                            ProfileState.Error(result.message, cached)
                        }
                    }
                }
        }
    }

    /**
     * Load game history
     */
    fun loadGameHistory(forceRefresh: Boolean = false, limit: Int = 50) {
        val userId = _currentUserId.value
        val walletAddress = _currentWalletAddress.value

        if (userId <= 0 || walletAddress == null) {
            _gameHistoryState.value = GameHistoryState.Error("No wallet connected")
            return
        }

        viewModelScope.launch {
            playerRepository.getGameHistory(walletAddress, userId, forceRefresh, limit)
                .collect { result ->
                    _gameHistoryState.value = when (result) {
                        is PlayerRepository.SyncResult.Loading -> GameHistoryState.Loading
                        is PlayerRepository.SyncResult.Success -> GameHistoryState.Success(result.data)
                        is PlayerRepository.SyncResult.Error -> {
                            @Suppress("UNCHECKED_CAST")
                            val cached = result.cachedData as? List<CachedGameHistory> ?: emptyList()
                            GameHistoryState.Error(result.message, cached)
                        }
                    }
                }
        }
    }

    /**
     * Refresh all player data
     */
    fun refresh() {
        _isRefreshing.value = true
        loadProfile(forceRefresh = true)
        loadGameHistory(forceRefresh = true)
        viewModelScope.launch {
            // Small delay to show refresh indicator
            kotlinx.coroutines.delay(500)
            _isRefreshing.value = false
        }
    }

    /**
     * Perform app launch sync
     */
    fun performAppLaunchSync() {
        val userId = _currentUserId.value
        val walletAddress = _currentWalletAddress.value

        if (userId <= 0 || walletAddress == null) {
            Log.d(TAG, "Skipping app launch sync - no wallet")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "Performing app launch sync for $walletAddress")
            playerRepository.syncPlayerData(walletAddress, userId)

            // Reload states
            loadProfile()
            loadGameHistory()
        }
    }

    /**
     * Sync after a game ends
     */
    fun syncAfterGame() {
        val userId = _currentUserId.value
        val walletAddress = _currentWalletAddress.value

        if (userId <= 0 || walletAddress == null) return

        viewModelScope.launch {
            Log.d(TAG, "Syncing after game")
            playerRepository.syncAfterGame(walletAddress, userId)

            // Reload states
            loadProfile(forceRefresh = true)
            loadGameHistory(forceRefresh = true, limit = 10)
        }
    }

    /**
     * Refresh X profile picture
     */
    fun refreshXProfilePicture() {
        val userId = _currentUserId.value
        val walletAddress = _currentWalletAddress.value

        if (userId <= 0 || walletAddress == null) return

        viewModelScope.launch {
            val newPictureUrl = playerRepository.refreshXProfilePicture(walletAddress, userId)
            if (newPictureUrl != null) {
                Log.d(TAG, "Profile picture refreshed: $newPictureUrl")
            }
        }
    }

    /**
     * Check if profile picture needs refresh
     */
    suspend fun shouldRefreshProfilePicture(): Boolean {
        return playerRepository.shouldRefreshProfilePicture()
    }

    /**
     * Mark profile picture as refreshed (call after X re-auth)
     */
    fun markProfilePictureRefreshed() {
        viewModelScope.launch {
            playerRepository.markProfilePictureRefreshed()
        }
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        val userId = _currentUserId.value
        if (userId <= 0) return

        viewModelScope.launch {
            playerRepository.clearCache(userId)
            _profileState.value = ProfileState.Initial
            _gameHistoryState.value = GameHistoryState.Initial
            _miniStats.value = null
        }
    }

    /**
     * Get current profile for use outside of flows
     */
    fun getCurrentProfile(): PlayerProfile? {
        return when (val state = _profileState.value) {
            is ProfileState.Success -> state.profile
            is ProfileState.Error -> state.cachedProfile
            else -> null
        }
    }

    /**
     * Get current game history for use outside of flows
     */
    fun getCurrentGameHistory(): List<CachedGameHistory> {
        return when (val state = _gameHistoryState.value) {
            is GameHistoryState.Success -> state.games
            is GameHistoryState.Error -> state.cachedGames
            else -> emptyList()
        }
    }
}
