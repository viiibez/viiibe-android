package com.viiibe.app.data.database

import androidx.room.*
import com.viiibe.app.data.model.ArcadeGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArcadeGameDao {

    // ============================================
    // Insert operations
    // ============================================

    @Insert
    suspend fun insertGame(game: ArcadeGameEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<ArcadeGameEntity>)

    // ============================================
    // Query operations - per user
    // ============================================

    @Query("SELECT * FROM arcade_games WHERE userId = :userId ORDER BY playedAt DESC")
    fun getGamesForUser(userId: Long): Flow<List<ArcadeGameEntity>>

    @Query("SELECT * FROM arcade_games WHERE userId = :userId ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentGamesForUser(userId: Long, limit: Int): Flow<List<ArcadeGameEntity>>

    @Query("SELECT * FROM arcade_games WHERE userId = :userId AND gameType = :gameType ORDER BY playedAt DESC")
    fun getGamesByTypeForUser(userId: Long, gameType: String): Flow<List<ArcadeGameEntity>>

    @Query("SELECT * FROM arcade_games WHERE id = :gameId")
    suspend fun getGameById(gameId: Long): ArcadeGameEntity?

    // ============================================
    // Sync operations
    // ============================================

    @Query("SELECT * FROM arcade_games WHERE userId = :userId AND synced = 0 ORDER BY playedAt ASC")
    suspend fun getUnsyncedGamesForUser(userId: Long): List<ArcadeGameEntity>

    @Query("UPDATE arcade_games SET synced = 1, syncedAt = :syncedAt WHERE id IN (:gameIds)")
    suspend fun markAsSynced(gameIds: List<Long>, syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE arcade_games SET synced = 1, syncedAt = :syncedAt WHERE id = :gameId")
    suspend fun markGameAsSynced(gameId: Long, syncedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM arcade_games WHERE userId = :userId AND synced = 0")
    suspend fun getUnsyncedCount(userId: Long): Int

    // ============================================
    // Statistics queries
    // ============================================

    @Query("SELECT COUNT(*) FROM arcade_games WHERE userId = :userId")
    fun getTotalGameCount(userId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM arcade_games WHERE userId = :userId AND won = 1")
    fun getTotalWins(userId: Long): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM arcade_games WHERE userId = :userId")
    fun getTotalPlayTime(userId: Long): Flow<Int?>

    @Query("SELECT MAX(score) FROM arcade_games WHERE userId = :userId AND gameType = :gameType")
    suspend fun getHighScoreForGame(userId: Long, gameType: String): Int?

    @Query("""
        SELECT gameType, COUNT(*) as count
        FROM arcade_games
        WHERE userId = :userId
        GROUP BY gameType
        ORDER BY count DESC
        LIMIT 1
    """)
    suspend fun getFavoriteGameType(userId: Long): ArcadeGameTypeCount?

    @Query("""
        SELECT gameType,
               COUNT(*) as totalGames,
               SUM(CASE WHEN won = 1 THEN 1 ELSE 0 END) as wins,
               MAX(score) as highScore,
               SUM(durationSeconds) as totalPlayTime
        FROM arcade_games
        WHERE userId = :userId
        GROUP BY gameType
    """)
    suspend fun getStatsByGameType(userId: Long): List<GameTypeStats>

    @Query("""
        SELECT gameType, difficulty,
               COUNT(*) as totalGames,
               SUM(CASE WHEN won = 1 THEN 1 ELSE 0 END) as wins,
               MAX(score) as highScore
        FROM arcade_games
        WHERE userId = :userId
        GROUP BY gameType, difficulty
    """)
    suspend fun getStatsByGameTypeAndDifficulty(userId: Long): List<GameTypeDifficultyStats>

    // ============================================
    // Delete operations
    // ============================================

    @Delete
    suspend fun deleteGame(game: ArcadeGameEntity)

    @Query("DELETE FROM arcade_games WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}

// Data classes for aggregate queries
data class ArcadeGameTypeCount(
    val gameType: String,
    val count: Int
)

data class GameTypeStats(
    val gameType: String,
    val totalGames: Int,
    val wins: Int,
    val highScore: Int,
    val totalPlayTime: Int
)

data class GameTypeDifficultyStats(
    val gameType: String,
    val difficulty: String,
    val totalGames: Int,
    val wins: Int,
    val highScore: Int
)
