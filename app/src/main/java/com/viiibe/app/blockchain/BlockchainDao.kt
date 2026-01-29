package com.viiibe.app.blockchain

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockchainDao {

    // Wallet operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet): Long

    @Update
    suspend fun updateWallet(wallet: Wallet)

    @Delete
    suspend fun deleteWallet(wallet: Wallet)

    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    fun getWalletForUser(userId: Long): Flow<Wallet?>

    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    suspend fun getWalletForUserSync(userId: Long): Wallet?

    @Query("SELECT * FROM wallets WHERE address = :address LIMIT 1")
    suspend fun getWalletByAddress(address: String): Wallet?

    // Achievement operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: Achievement): Long

    @Update
    suspend fun updateAchievement(achievement: Achievement)

    @Query("SELECT * FROM achievements WHERE userId = :userId ORDER BY earnedAt DESC")
    fun getAchievementsForUser(userId: Long): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE userId = :userId AND type = :type LIMIT 1")
    suspend fun getAchievementByType(userId: Long, type: AchievementType): Achievement?

    @Query("SELECT * FROM achievements WHERE userId = :userId AND isMinted = 0")
    fun getUnmintedAchievements(userId: Long): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE userId = :userId AND isMinted = 1")
    fun getMintedAchievements(userId: Long): Flow<List<Achievement>>

    @Query("SELECT COUNT(*) FROM achievements WHERE userId = :userId AND isMinted = 1")
    fun getMintedAchievementCount(userId: Long): Flow<Int>

    // On-chain workout operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnChainWorkout(onChainWorkout: OnChainWorkout): Long

    @Query("SELECT * FROM onchain_workouts WHERE userId = :userId ORDER BY recordedAt DESC")
    fun getOnChainWorkoutsForUser(userId: Long): Flow<List<OnChainWorkout>>

    @Query("SELECT * FROM onchain_workouts WHERE workoutId = :workoutId LIMIT 1")
    suspend fun getOnChainWorkoutByWorkoutId(workoutId: Long): OnChainWorkout?

    @Query("SELECT COUNT(*) FROM onchain_workouts WHERE userId = :userId")
    fun getOnChainWorkoutCount(userId: Long): Flow<Int>

    // Check if workout is already on chain
    @Query("SELECT EXISTS(SELECT 1 FROM onchain_workouts WHERE workoutId = :workoutId)")
    suspend fun isWorkoutOnChain(workoutId: Long): Boolean
}
