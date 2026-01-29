package com.viiibe.app.data.database

import androidx.room.*
import com.viiibe.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserByIdFlow(userId: Long): Flow<User?>

    @Query("SELECT * FROM users WHERE isActive = 1 LIMIT 1")
    fun getActiveUser(): Flow<User?>

    @Query("SELECT * FROM users WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveUserOnce(): User?

    @Query("SELECT COUNT(*) FROM users")
    fun getUserCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCountOnce(): Int

    /**
     * Set a user as active and deactivate all others
     */
    @Transaction
    suspend fun setActiveUser(userId: Long) {
        deactivateAllUsers()
        activateUser(userId)
    }

    @Query("UPDATE users SET isActive = 0")
    suspend fun deactivateAllUsers()

    @Query("UPDATE users SET isActive = 1 WHERE id = :userId")
    suspend fun activateUser(userId: Long)

    /**
     * Update user settings
     */
    @Query("UPDATE users SET useMetricUnits = :useMetric WHERE id = :userId")
    suspend fun updateMetricPreference(userId: Long, useMetric: Boolean)

    @Query("UPDATE users SET maxHeartRate = :maxHr WHERE id = :userId")
    suspend fun updateMaxHeartRate(userId: Long, maxHr: Int)

    @Query("UPDATE users SET ftpWatts = :ftp WHERE id = :userId")
    suspend fun updateFtp(userId: Long, ftp: Int)
}
