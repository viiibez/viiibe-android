package com.viiibe.app.data.repository

import android.content.Context
import android.util.Log
import com.viiibe.app.BuildConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.viiibe.app.data.database.ArcadeGameDao
import com.viiibe.app.data.database.PlayerDao
import com.viiibe.app.data.database.WorkoutDao
import com.viiibe.app.data.model.*
import com.viiibe.app.data.sync.HistorySyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_prefs")

/**
 * Repository for managing player profile data.
 * Handles fetching from backend API, caching locally, and offline viewing.
 */
class PlayerRepository(
    private val context: Context,
    private val playerDao: PlayerDao,
    private val arcadeGameDao: ArcadeGameDao? = null,
    private val workoutDao: WorkoutDao? = null
) {
    // History sync manager - only created if DAOs are provided
    private val historySyncManager: HistorySyncManager? = if (arcadeGameDao != null && workoutDao != null) {
        HistorySyncManager(arcadeGameDao, workoutDao)
    } else null
    companion object {
        private const val TAG = "PlayerRepository"
        private val BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL

        // Sync intervals
        private const val PROFILE_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val PROFILE_PICTURE_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

        // DataStore keys
        private val KEY_LAST_SYNC = longPreferencesKey("last_profile_sync")
        private val KEY_LAST_PICTURE_REFRESH = longPreferencesKey("last_picture_refresh")
        private val KEY_CACHED_PROFILE = stringPreferencesKey("cached_profile_json")
        private val KEY_CACHED_HISTORY = stringPreferencesKey("cached_history_json")
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dataStore = context.playerDataStore

    /**
     * Sync result class for reporting sync status
     */
    sealed class SyncResult<out T> {
        data class Success<T>(val data: T) : SyncResult<T>()
        data class Error(val message: String, val cachedData: Any? = null) : SyncResult<Nothing>()
        object Loading : SyncResult<Nothing>()
    }

    // ==================== Profile Fetching ====================

    /**
     * Get player profile, fetching from network if stale or missing.
     * Falls back to cache for offline viewing.
     */
    fun getPlayerProfile(
        walletAddress: String,
        userId: Long,
        forceRefresh: Boolean = false
    ): Flow<SyncResult<PlayerProfile>> = flow {
        emit(SyncResult.Loading)

        try {
            // Check if we should sync
            val lastSync = dataStore.data.map { it[KEY_LAST_SYNC] ?: 0L }.first()
            val shouldSync = forceRefresh ||
                System.currentTimeMillis() - lastSync > PROFILE_SYNC_INTERVAL_MS

            if (shouldSync) {
                // Try to fetch from network
                val networkResult = fetchProfileFromNetwork(walletAddress, userId)
                if (networkResult != null) {
                    // Update last sync time
                    dataStore.edit { it[KEY_LAST_SYNC] = System.currentTimeMillis() }
                    emit(SyncResult.Success(networkResult))
                    return@flow
                }
            }

            // Fall back to cache
            val cachedProfile = playerDao.getPlayerProfile(userId)
            if (cachedProfile != null) {
                emit(SyncResult.Success(cachedProfile.toPlayerProfile()))
            } else {
                emit(SyncResult.Error("No profile data available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting player profile", e)

            // Try to return cached data
            val cachedProfile = playerDao.getPlayerProfile(userId)
            if (cachedProfile != null) {
                emit(SyncResult.Error("Network error. Showing cached data.", cachedProfile.toPlayerProfile()))
            } else {
                emit(SyncResult.Error("Failed to load profile: ${e.message}"))
            }
        }
    }

    /**
     * Fetch profile from backend API and merge with auth profile
     */
    private suspend fun fetchProfileFromNetwork(
        walletAddress: String,
        userId: Long
    ): PlayerProfile? = withContext(Dispatchers.IO) {
        try {
            // Fetch player stats
            val statsRequest = Request.Builder()
                .url("$BACKEND_BASE_URL/leaderboard/player/${walletAddress.lowercase()}")
                .get()
                .build()

            val statsResponse = httpClient.newCall(statsRequest).execute()
            var statsData: PlayerStatsData? = null

            if (statsResponse.isSuccessful) {
                val body = statsResponse.body?.string()
                if (body != null) {
                    val response = gson.fromJson(body, PlayerStatsResponse::class.java)
                    statsData = response.data
                }
            }

            // Fetch auth profile (X account info)
            val authRequest = Request.Builder()
                .url("$BACKEND_BASE_URL/auth/profile/${walletAddress.lowercase()}")
                .get()
                .build()

            val authResponse = httpClient.newCall(authRequest).execute()
            var authData: AuthProfileData? = null

            if (authResponse.isSuccessful) {
                val body = authResponse.body?.string()
                if (body != null) {
                    val response = gson.fromJson(body, AuthProfileResponse::class.java)
                    authData = response.data
                }
            }

            // Combine the data
            val profile = PlayerProfile(
                walletAddress = walletAddress,
                xUsername = authData?.xUsername,
                xProfilePicture = authData?.xProfilePicture,
                totalGames = statsData?.totalGames ?: 0,
                wins = statsData?.wins ?: 0,
                losses = statsData?.losses ?: 0,
                winRate = (statsData?.winRate ?: 0).toFloat(),
                totalEarnings = parseWeiToEther(statsData?.totalWon ?: "0"),
                totalWagered = parseWeiToEther(statsData?.totalWagered ?: "0"),
                currentStreak = statsData?.currentStreak ?: 0,
                bestStreak = statsData?.bestStreak ?: 0,
                favoriteGame = statsData?.favoriteGame,
                rank = null, // Could be fetched from leaderboard
                joinedAt = statsData?.createdAt?.let { formatTimestamp(it) }
                    ?: authData?.createdAt?.let { formatTimestamp(it) }
            )

            // Cache the profile
            cacheProfile(profile, userId)

            profile
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile from network", e)
            null
        }
    }

    // ==================== Game History ====================

    /**
     * Get game history for a player
     */
    fun getGameHistory(
        walletAddress: String,
        userId: Long,
        forceRefresh: Boolean = false,
        limit: Int = 50
    ): Flow<SyncResult<List<CachedGameHistory>>> = flow {
        emit(SyncResult.Loading)

        try {
            if (forceRefresh) {
                val networkResult = fetchGameHistoryFromNetwork(walletAddress, userId, limit)
                if (networkResult != null) {
                    emit(SyncResult.Success(networkResult))
                    return@flow
                }
            }

            // Fall back to cache
            val cachedHistory = playerDao.getGameHistory(userId, limit)
            if (cachedHistory.isNotEmpty()) {
                emit(SyncResult.Success(cachedHistory))
            } else if (forceRefresh) {
                emit(SyncResult.Error("No game history available"))
            } else {
                // Try network if cache is empty and not forced
                val networkResult = fetchGameHistoryFromNetwork(walletAddress, userId, limit)
                if (networkResult != null) {
                    emit(SyncResult.Success(networkResult))
                } else {
                    emit(SyncResult.Error("No game history available"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting game history", e)

            val cachedHistory = playerDao.getGameHistory(userId, limit)
            if (cachedHistory.isNotEmpty()) {
                emit(SyncResult.Error("Network error. Showing cached data.", cachedHistory))
            } else {
                emit(SyncResult.Error("Failed to load history: ${e.message}"))
            }
        }
    }

    /**
     * Fetch game history from backend API
     */
    private suspend fun fetchGameHistoryFromNetwork(
        walletAddress: String,
        userId: Long,
        limit: Int
    ): List<CachedGameHistory>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/games/player/${walletAddress.lowercase()}")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch game history: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val gameResponse = gson.fromJson(body, GameHistoryResponse::class.java)

            if (!gameResponse.success) {
                return@withContext null
            }

            // Convert to cached format
            val cachedHistory = gameResponse.data.take(limit).map { it.toCached(userId) }

            // Cache the history
            playerDao.clearGameHistory(userId)
            playerDao.insertGameHistory(cachedHistory)

            cachedHistory
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching game history from network", e)
            null
        }
    }

    // ==================== X Profile Picture Refresh ====================

    /**
     * Check if X profile picture needs refresh (older than 24 hours)
     */
    suspend fun shouldRefreshProfilePicture(): Boolean {
        val lastRefresh = dataStore.data.map { it[KEY_LAST_PICTURE_REFRESH] ?: 0L }.first()
        return System.currentTimeMillis() - lastRefresh > PROFILE_PICTURE_REFRESH_INTERVAL_MS
    }

    /**
     * Refresh X profile picture from backend
     */
    suspend fun refreshXProfilePicture(walletAddress: String, userId: Long): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BACKEND_BASE_URL/auth/profile/${walletAddress.lowercase()}")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val authResponse = gson.fromJson(body, AuthProfileResponse::class.java)
                val profilePicture = authResponse.data?.xProfilePicture

                // Update cache with new picture URL
                if (profilePicture != null) {
                    playerDao.updateProfilePicture(
                        userId = userId,
                        xProfilePicture = profilePicture,
                        refreshedAt = System.currentTimeMillis()
                    )
                }

                // Update last refresh time
                dataStore.edit { it[KEY_LAST_PICTURE_REFRESH] = System.currentTimeMillis() }

                profilePicture
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing X profile picture", e)
                null
            }
        }

    /**
     * Mark profile picture as recently refreshed (e.g., after X re-auth)
     */
    suspend fun markProfilePictureRefreshed() {
        dataStore.edit { it[KEY_LAST_PICTURE_REFRESH] = System.currentTimeMillis() }
    }

    // ==================== Sync Operations ====================

    /**
     * Full sync of player data - call on app launch
     */
    suspend fun syncPlayerData(walletAddress: String, userId: Long) {
        Log.d(TAG, "Starting player data sync for $walletAddress")

        try {
            // First, upload any unsynced local data (arcade games, workouts)
            syncLocalHistory(userId, walletAddress)

            // Fetch profile
            fetchProfileFromNetwork(walletAddress, userId)

            // Fetch game history
            fetchGameHistoryFromNetwork(walletAddress, userId, 50)

            // Check if profile picture needs refresh
            if (shouldRefreshProfilePicture()) {
                refreshXProfilePicture(walletAddress, userId)
            }

            Log.d(TAG, "Player data sync completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during player data sync", e)
        }
    }

    /**
     * Sync local history data (arcade games, workouts) to backend
     */
    suspend fun syncLocalHistory(userId: Long, walletAddress: String): HistorySyncManager.SyncResult? {
        return try {
            val result = historySyncManager?.syncAllForUser(userId, walletAddress)
            if (result != null) {
                Log.d(TAG, "Local history sync: ${result.totalSynced} items synced")
                if (result.hasErrors) {
                    Log.w(TAG, "History sync had errors: ${result.errors}")
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing local history", e)
            null
        }
    }

    /**
     * Check if there are unsynced history items
     */
    suspend fun hasUnsyncedHistory(userId: Long): Boolean {
        return historySyncManager?.hasUnsyncedItems(userId) ?: false
    }

    /**
     * Get count of unsynced history items
     */
    suspend fun getUnsyncedHistoryCount(userId: Long): Int {
        return historySyncManager?.getUnsyncedCount(userId) ?: 0
    }

    /**
     * Sync after a game ends
     */
    suspend fun syncAfterGame(walletAddress: String, userId: Long) {
        Log.d(TAG, "Syncing player data after game")

        try {
            // First, sync any local arcade games/workouts to backend
            syncLocalHistory(userId, walletAddress)

            // Then refresh profile and recent history from network
            fetchProfileFromNetwork(walletAddress, userId)
            fetchGameHistoryFromNetwork(walletAddress, userId, 10)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing after game", e)
        }
    }

    // ==================== Cache Operations ====================

    /**
     * Cache player profile locally
     */
    private suspend fun cacheProfile(profile: PlayerProfile, userId: Long) {
        val cached = CachedPlayerProfile(
            userId = userId,
            walletAddress = profile.walletAddress,
            xUsername = profile.xUsername,
            xProfilePicture = profile.xProfilePicture,
            totalGames = profile.totalGames,
            wins = profile.wins,
            losses = profile.losses,
            winRate = profile.winRate,
            totalEarnings = profile.totalEarnings,
            totalWagered = profile.totalWagered,
            currentStreak = profile.currentStreak,
            bestStreak = profile.bestStreak,
            favoriteGame = profile.favoriteGame,
            rank = profile.rank,
            joinedAt = profile.joinedAt,
            lastSyncedAt = System.currentTimeMillis()
        )

        playerDao.insertOrUpdateProfile(cached)
    }

    /**
     * Clear all cached data for a user
     */
    suspend fun clearCache(userId: Long) {
        playerDao.clearPlayerProfile(userId)
        playerDao.clearGameHistory(userId)
        dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_SYNC)
            prefs.remove(KEY_LAST_PICTURE_REFRESH)
        }
    }

    /**
     * Get cached profile without network call
     */
    fun getCachedProfile(userId: Long): Flow<CachedPlayerProfile?> {
        return playerDao.getPlayerProfileFlow(userId)
    }

    /**
     * Get cached game history without network call
     */
    fun getCachedGameHistory(userId: Long, limit: Int = 50): Flow<List<CachedGameHistory>> {
        return playerDao.getGameHistoryFlow(userId, limit)
    }

    // ==================== Mini Stats for Home Screen ====================

    /**
     * Get mini stats for display on home screen
     */
    data class MiniStats(
        val gamesPlayed: Int,
        val winRate: Float,
        val totalEarnings: Double,
        val xProfilePicture: String?
    )

    fun getMiniStats(userId: Long): Flow<MiniStats?> {
        return playerDao.getPlayerProfileFlow(userId).map { profile ->
            profile?.let {
                MiniStats(
                    gamesPlayed = it.totalGames,
                    winRate = it.winRate,
                    totalEarnings = it.totalEarnings,
                    xProfilePicture = it.xProfilePicture
                )
            }
        }
    }

    // ==================== Utility Functions ====================

    /**
     * Parse Wei (as string) to Ether (as double)
     */
    private fun parseWeiToEther(weiString: String): Double {
        return try {
            val wei = weiString.toBigDecimalOrNull() ?: return 0.0
            wei.divide(java.math.BigDecimal("1000000000000000000")).toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Format timestamp to readable date string
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
