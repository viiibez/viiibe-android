package com.viiibe.app.data.sync

import android.util.Log
import com.google.gson.Gson
import com.viiibe.app.BuildConfig
import com.viiibe.app.data.database.ArcadeGameDao
import com.viiibe.app.data.database.WorkoutDao
import com.viiibe.app.data.model.ArcadeGameEntity
import com.viiibe.app.data.model.ArcadeGameSyncDto
import com.viiibe.app.data.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Manages syncing local history data (arcade games, workouts) to the backend.
 * Called when a wallet is connected to sync unsynced data.
 */
class HistorySyncManager(
    private val arcadeGameDao: ArcadeGameDao,
    private val workoutDao: WorkoutDao
) {
    companion object {
        private const val TAG = "HistorySyncManager"
        private val BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL
        private const val BATCH_SIZE = 50
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Result of a sync operation
     */
    data class SyncResult(
        val arcadeGamesSynced: Int,
        val workoutsSynced: Int,
        val errors: List<String>
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
        val totalSynced: Int get() = arcadeGamesSynced + workoutsSynced
    }

    /**
     * Sync all unsynced data for a user to the backend
     */
    suspend fun syncAllForUser(userId: Long, walletAddress: String): SyncResult {
        Log.d(TAG, "Starting history sync for user $userId, wallet $walletAddress")

        val errors = mutableListOf<String>()
        var arcadeGamesSynced = 0
        var workoutsSynced = 0

        // Sync arcade games
        try {
            arcadeGamesSynced = syncArcadeGames(userId, walletAddress)
            Log.d(TAG, "Synced $arcadeGamesSynced arcade games")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing arcade games", e)
            errors.add("Failed to sync arcade games: ${e.message}")
        }

        // Sync workouts
        try {
            workoutsSynced = syncWorkouts(userId, walletAddress)
            Log.d(TAG, "Synced $workoutsSynced workouts")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing workouts", e)
            errors.add("Failed to sync workouts: ${e.message}")
        }

        val result = SyncResult(arcadeGamesSynced, workoutsSynced, errors)
        Log.d(TAG, "History sync completed: $result")
        return result
    }

    /**
     * Sync unsynced arcade games to the backend
     */
    private suspend fun syncArcadeGames(userId: Long, walletAddress: String): Int =
        withContext(Dispatchers.IO) {
            val unsynced = arcadeGameDao.getUnsyncedGamesForUser(userId)
            if (unsynced.isEmpty()) {
                return@withContext 0
            }

            Log.d(TAG, "Found ${unsynced.size} unsynced arcade games")

            var totalSynced = 0

            // Process in batches
            unsynced.chunked(BATCH_SIZE).forEach { batch ->
                val syncDtos = batch.map { entity ->
                    ArcadeGameSyncDto.fromEntity(entity, walletAddress)
                }

                val requestBody = mapOf("games" to syncDtos.map { dto ->
                    mapOf(
                        "walletAddress" to dto.walletAddress,
                        "gameType" to dto.gameType,
                        "difficulty" to dto.difficulty,
                        "score" to dto.score,
                        "won" to dto.won,
                        "durationSeconds" to dto.durationSeconds,
                        "details" to dto.details,
                        "playedAt" to isoFormatter.format(Date(dto.playedAt))
                    )
                })

                val json = gson.toJson(requestBody)
                val request = Request.Builder()
                    .url("$BACKEND_BASE_URL/api/history/single-player")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                try {
                    val response = httpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        // Mark games as synced
                        val gameIds = batch.map { it.id }
                        arcadeGameDao.markAsSynced(gameIds)
                        totalSynced += batch.size
                        Log.d(TAG, "Successfully synced ${batch.size} arcade games")
                    } else {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "Failed to sync arcade games: ${response.code} - $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing arcade games batch", e)
                }
            }

            totalSynced
        }

    /**
     * Sync unsynced workouts to the backend
     */
    private suspend fun syncWorkouts(userId: Long, walletAddress: String): Int =
        withContext(Dispatchers.IO) {
            val unsynced = workoutDao.getUnsyncedWorkoutsForUser(userId)
            if (unsynced.isEmpty()) {
                return@withContext 0
            }

            Log.d(TAG, "Found ${unsynced.size} unsynced workouts")

            var totalSynced = 0

            // Process in batches
            unsynced.chunked(BATCH_SIZE).forEach { batch ->
                val workoutDtos = batch.map { workout ->
                    mapOf(
                        "walletAddress" to walletAddress,
                        "mobileWorkoutId" to workout.id,
                        "startTime" to isoFormatter.format(Date(workout.startTime)),
                        "durationSeconds" to workout.durationSeconds,
                        "totalOutputKj" to workout.totalOutput,
                        "avgCadence" to workout.avgCadence,
                        "avgHeartRate" to workout.avgHeartRate,
                        "caloriesBurned" to workout.caloriesBurned,
                        "totalDistanceMiles" to workout.totalDistance,
                        "workoutType" to workout.workoutType,
                        "videoTitle" to workout.videoTitle
                    )
                }

                val requestBody = mapOf("workouts" to workoutDtos)
                val json = gson.toJson(requestBody)

                val request = Request.Builder()
                    .url("$BACKEND_BASE_URL/api/history/workouts")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                try {
                    val response = httpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        // Mark workouts as synced
                        val workoutIds = batch.map { it.id }
                        workoutDao.markAsSynced(workoutIds)
                        totalSynced += batch.size
                        Log.d(TAG, "Successfully synced ${batch.size} workouts")
                    } else {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "Failed to sync workouts: ${response.code} - $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing workouts batch", e)
                }
            }

            totalSynced
        }

    /**
     * Get the count of unsynced items for a user
     */
    suspend fun getUnsyncedCount(userId: Long): Int {
        val arcadeCount = arcadeGameDao.getUnsyncedCount(userId)
        val workoutCount = workoutDao.getUnsyncedCount(userId)
        return arcadeCount + workoutCount
    }

    /**
     * Check if there are any items to sync
     */
    suspend fun hasUnsyncedItems(userId: Long): Boolean {
        return getUnsyncedCount(userId) > 0
    }
}
