package com.viiibe.app.data.database

import androidx.room.*
import com.viiibe.app.data.model.CachedGameHistory
import com.viiibe.app.data.model.CachedPlayerProfile
import kotlinx.coroutines.flow.Flow

/**
 * DAO for player profile and game history caching
 */
@Dao
interface PlayerDao {

    // ==================== Player Profile ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: CachedPlayerProfile)

    @Query("SELECT * FROM player_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getPlayerProfile(userId: Long): CachedPlayerProfile?

    @Query("SELECT * FROM player_profiles WHERE userId = :userId LIMIT 1")
    fun getPlayerProfileFlow(userId: Long): Flow<CachedPlayerProfile?>

    @Query("SELECT * FROM player_profiles WHERE walletAddress = :walletAddress LIMIT 1")
    suspend fun getPlayerProfileByWallet(walletAddress: String): CachedPlayerProfile?

    @Query("""
        UPDATE player_profiles
        SET xProfilePicture = :xProfilePicture,
            profilePictureRefreshedAt = :refreshedAt
        WHERE userId = :userId
    """)
    suspend fun updateProfilePicture(userId: Long, xProfilePicture: String, refreshedAt: Long)

    @Query("""
        UPDATE player_profiles
        SET totalGames = :totalGames,
            wins = :wins,
            losses = :losses,
            winRate = :winRate,
            totalEarnings = :totalEarnings,
            totalWagered = :totalWagered,
            currentStreak = :currentStreak,
            bestStreak = :bestStreak,
            lastSyncedAt = :syncedAt
        WHERE userId = :userId
    """)
    suspend fun updateStats(
        userId: Long,
        totalGames: Int,
        wins: Int,
        losses: Int,
        winRate: Float,
        totalEarnings: Double,
        totalWagered: Double,
        currentStreak: Int,
        bestStreak: Int,
        syncedAt: Long
    )

    @Query("DELETE FROM player_profiles WHERE userId = :userId")
    suspend fun clearPlayerProfile(userId: Long)

    @Query("SELECT lastSyncedAt FROM player_profiles WHERE userId = :userId")
    suspend fun getLastSyncTime(userId: Long): Long?

    @Query("SELECT profilePictureRefreshedAt FROM player_profiles WHERE userId = :userId")
    suspend fun getLastPictureRefreshTime(userId: Long): Long?

    // ==================== Game History ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameHistory(games: List<CachedGameHistory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameHistoryItem(game: CachedGameHistory)

    @Query("SELECT * FROM game_history WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getGameHistory(userId: Long, limit: Int): List<CachedGameHistory>

    @Query("SELECT * FROM game_history WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    fun getGameHistoryFlow(userId: Long, limit: Int): Flow<List<CachedGameHistory>>

    @Query("SELECT * FROM game_history WHERE gameId = :gameId LIMIT 1")
    suspend fun getGameById(gameId: String): CachedGameHistory?

    @Query("DELETE FROM game_history WHERE userId = :userId")
    suspend fun clearGameHistory(userId: Long)

    @Query("SELECT COUNT(*) FROM game_history WHERE userId = :userId")
    suspend fun getGameHistoryCount(userId: Long): Int

    // ==================== Stats Queries ====================

    @Query("""
        SELECT COUNT(*) FROM game_history
        WHERE userId = :userId
        AND winnerAddress = :walletAddress
    """)
    suspend fun getWinCount(userId: Long, walletAddress: String): Int

    @Query("""
        SELECT COUNT(*) FROM game_history
        WHERE userId = :userId
        AND winnerAddress IS NOT NULL
        AND winnerAddress != :walletAddress
        AND (hostAddress = :walletAddress OR guestAddress = :walletAddress)
    """)
    suspend fun getLossCount(userId: Long, walletAddress: String): Int

    @Query("""
        SELECT SUM(stakeAmount) FROM game_history
        WHERE userId = :userId
        AND winnerAddress = :walletAddress
        AND stakeAmount IS NOT NULL
    """)
    suspend fun getTotalWinnings(userId: Long, walletAddress: String): Double?

    @Query("""
        SELECT * FROM game_history
        WHERE userId = :userId
        AND gameType = :gameType
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getGameHistoryByType(userId: Long, gameType: String, limit: Int): List<CachedGameHistory>

    @Query("""
        SELECT gameType, COUNT(*) as count
        FROM game_history
        WHERE userId = :userId
        GROUP BY gameType
        ORDER BY count DESC
        LIMIT 1
    """)
    suspend fun getMostPlayedGameType(userId: Long): GameTypeCount?

    // ==================== Recent Activity ====================

    @Query("""
        SELECT * FROM game_history
        WHERE userId = :userId
        AND createdAt > :since
        ORDER BY createdAt DESC
    """)
    suspend fun getRecentGames(userId: Long, since: Long): List<CachedGameHistory>

    @Query("""
        SELECT * FROM game_history
        WHERE userId = :userId
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun getLastGame(userId: Long): CachedGameHistory?
}

/**
 * Helper data class for game type count query
 */
data class GameTypeCount(
    val gameType: String,
    val count: Int
)
