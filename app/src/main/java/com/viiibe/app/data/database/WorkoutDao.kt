package com.viiibe.app.data.database

import androidx.room.*
import com.viiibe.app.data.model.Workout
import com.viiibe.app.data.model.WorkoutSample
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // Workout operations
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    // Per-user workout queries
    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC")
    fun getWorkoutsForUser(userId: Long): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC LIMIT :limit")
    fun getRecentWorkoutsForUser(userId: Long, limit: Int): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): Workout?

    @Query("SELECT * FROM workouts WHERE userId = :userId AND startTime >= :startDate AND startTime <= :endDate ORDER BY startTime DESC")
    fun getWorkoutsInRangeForUser(userId: Long, startDate: Long, endDate: Long): Flow<List<Workout>>

    // Per-user stats (Flow versions)
    @Query("SELECT COUNT(*) FROM workouts WHERE userId = :userId")
    fun getTotalWorkoutCountForUser(userId: Long): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM workouts WHERE userId = :userId")
    fun getTotalWorkoutSecondsForUser(userId: Long): Flow<Int?>

    @Query("SELECT SUM(totalOutput) FROM workouts WHERE userId = :userId")
    fun getTotalOutputForUser(userId: Long): Flow<Int?>

    @Query("SELECT SUM(caloriesBurned) FROM workouts WHERE userId = :userId")
    fun getTotalCaloriesForUser(userId: Long): Flow<Int?>

    @Query("SELECT SUM(totalDistance) FROM workouts WHERE userId = :userId")
    fun getTotalDistanceForUser(userId: Long): Flow<Float?>

    // Per-user stats (suspend versions for blockchain achievement checking)
    @Query("SELECT COUNT(*) FROM workouts WHERE userId = :userId")
    suspend fun getWorkoutCountForUserSync(userId: Long): Int

    @Query("SELECT SUM(durationSeconds) FROM workouts WHERE userId = :userId")
    suspend fun getTotalMinutesForUserSync(userId: Long): Int?

    @Query("SELECT SUM(totalOutput) FROM workouts WHERE userId = :userId")
    suspend fun getTotalOutputForUserSync(userId: Long): Int?

    @Query("SELECT SUM(caloriesBurned) FROM workouts WHERE userId = :userId")
    suspend fun getTotalCaloriesForUserSync(userId: Long): Int?

    @Query("SELECT SUM(totalDistance) FROM workouts WHERE userId = :userId")
    suspend fun getTotalDistanceForUserSync(userId: Long): Float?

    @Query("SELECT AVG(avgCadence) FROM workouts WHERE userId = :userId AND avgCadence > 0")
    fun getAverageCadenceForUser(userId: Long): Flow<Float?>

    // Weekly stats for user
    @Query("""
        SELECT * FROM workouts
        WHERE userId = :userId AND startTime >= :weekStart
        ORDER BY startTime DESC
    """)
    fun getWorkoutsThisWeekForUser(userId: Long, weekStart: Long): Flow<List<Workout>>

    // All users queries (for admin/overview purposes)
    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT COUNT(*) FROM workouts")
    fun getTotalWorkoutCount(): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM workouts")
    fun getTotalWorkoutSeconds(): Flow<Int?>

    @Query("SELECT SUM(totalOutput) FROM workouts")
    fun getTotalOutput(): Flow<Int?>

    @Query("SELECT SUM(caloriesBurned) FROM workouts")
    fun getTotalCalories(): Flow<Int?>

    @Query("SELECT SUM(totalDistance) FROM workouts")
    fun getTotalDistance(): Flow<Float?>

    // Workout sample operations
    @Insert
    suspend fun insertSample(sample: WorkoutSample)

    @Insert
    suspend fun insertSamples(samples: List<WorkoutSample>)

    @Query("SELECT * FROM workout_samples WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getSamplesForWorkout(workoutId: Long): List<WorkoutSample>

    @Query("DELETE FROM workout_samples WHERE workoutId = :workoutId")
    suspend fun deleteSamplesForWorkout(workoutId: Long)

    // ============================================
    // Sync operations
    // ============================================

    @Query("SELECT * FROM workouts WHERE userId = :userId AND synced = 0 ORDER BY startTime ASC")
    suspend fun getUnsyncedWorkoutsForUser(userId: Long): List<Workout>

    @Query("UPDATE workouts SET synced = 1, syncedAt = :syncedAt WHERE id IN (:workoutIds)")
    suspend fun markAsSynced(workoutIds: List<Long>, syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workouts SET synced = 1, syncedAt = :syncedAt WHERE id = :workoutId")
    suspend fun markWorkoutAsSynced(workoutId: Long, syncedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM workouts WHERE userId = :userId AND synced = 0")
    suspend fun getUnsyncedCount(userId: Long): Int
}
