package com.viiibe.app.arcade.p2p

import androidx.room.*
import com.viiibe.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "p2p_sessions",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["sessionId"])]
)
data class P2PSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val sessionId: String,
    val gameType: String,
    val hostAddress: String,
    val guestAddress: String?,
    val stake: String,              // Stored as string for precision
    val status: String,             // CREATED, MATCHED, PLAYING, SETTLED, DISPUTED, CANCELLED
    val winner: String?,            // Winner wallet address
    val hostScore: Int = 0,
    val guestScore: Int = 0,
    val stakeTxHash: String?,
    val settleTxHash: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val settledAt: Long? = null
)

@Entity(
    tableName = "wagers",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["gameId"])]
)
data class WagerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val gameId: String,
    val sessionId: String?,         // Local P2P session ID if applicable
    val myAddress: String,
    val opponentAddress: String?,
    val myStake: String,
    val opponentStake: String?,
    val gameType: String,
    val result: String,             // WON, LOST, TIED, DISPUTED, PENDING
    val payout: String?,
    val stakeTxHash: String?,
    val settleTxHash: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val settledAt: Long? = null
)

@Entity(
    tableName = "spectator_bets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["gameId"])]
)
data class SpectatorBetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val gameId: String,
    val predictedWinner: String,    // Address of player we bet on
    val amount: String,
    val odds: Double,
    val result: String,             // WON, LOST, PENDING, CANCELLED
    val payout: String?,
    val betTxHash: String?,
    val claimTxHash: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val claimedAt: Long? = null
)

@Dao
interface P2PDao {
    // P2P Sessions
    @Query("SELECT * FROM p2p_sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSessionsForUser(userId: Long): Flow<List<P2PSessionEntity>>

    @Query("SELECT * FROM p2p_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): P2PSessionEntity?

    @Query("SELECT * FROM p2p_sessions WHERE userId = :userId AND status = 'PLAYING'")
    suspend fun getActiveSession(userId: Long): P2PSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: P2PSessionEntity): Long

    @Update
    suspend fun updateSession(session: P2PSessionEntity)

    @Query("UPDATE p2p_sessions SET status = :status, settledAt = :settledAt WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String, settledAt: Long? = null)

    @Query("UPDATE p2p_sessions SET winner = :winner, hostScore = :hostScore, guestScore = :guestScore, status = 'SETTLED', settleTxHash = :txHash, settledAt = :settledAt WHERE sessionId = :sessionId")
    suspend fun settleSession(
        sessionId: String,
        winner: String?,
        hostScore: Int,
        guestScore: Int,
        txHash: String,
        settledAt: Long = System.currentTimeMillis()
    )

    // Wagers
    @Query("SELECT * FROM wagers WHERE userId = :userId ORDER BY createdAt DESC")
    fun getWagersForUser(userId: Long): Flow<List<WagerEntity>>

    @Query("SELECT * FROM wagers WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentWagers(userId: Long, limit: Int = 10): Flow<List<WagerEntity>>

    @Query("SELECT * FROM wagers WHERE gameId = :gameId")
    suspend fun getWagerByGameId(gameId: String): WagerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWager(wager: WagerEntity): Long

    @Update
    suspend fun updateWager(wager: WagerEntity)

    @Query("UPDATE wagers SET result = :result, payout = :payout, settleTxHash = :txHash, settledAt = :settledAt WHERE gameId = :gameId")
    suspend fun settleWager(
        gameId: String,
        result: String,
        payout: String?,
        txHash: String,
        settledAt: Long = System.currentTimeMillis()
    )

    // Wager statistics
    @Query("SELECT COUNT(*) FROM wagers WHERE userId = :userId AND result = 'WON'")
    suspend fun getWinCount(userId: Long): Int

    @Query("SELECT COUNT(*) FROM wagers WHERE userId = :userId AND result = 'LOST'")
    suspend fun getLossCount(userId: Long): Int

    @Query("SELECT SUM(CAST(payout AS REAL)) FROM wagers WHERE userId = :userId AND result = 'WON'")
    suspend fun getTotalWinnings(userId: Long): Double?

    @Query("SELECT SUM(CAST(myStake AS REAL)) FROM wagers WHERE userId = :userId AND result = 'LOST'")
    suspend fun getTotalLosses(userId: Long): Double?

    // Spectator Bets
    @Query("SELECT * FROM spectator_bets WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSpectatorBetsForUser(userId: Long): Flow<List<SpectatorBetEntity>>

    @Query("SELECT * FROM spectator_bets WHERE gameId = :gameId AND userId = :userId")
    suspend fun getSpectatorBetForGame(gameId: String, userId: Long): SpectatorBetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpectatorBet(bet: SpectatorBetEntity): Long

    @Update
    suspend fun updateSpectatorBet(bet: SpectatorBetEntity)

    @Query("UPDATE spectator_bets SET result = :result, payout = :payout, claimTxHash = :txHash, claimedAt = :claimedAt WHERE id = :betId")
    suspend fun claimSpectatorBet(
        betId: Long,
        result: String,
        payout: String?,
        txHash: String,
        claimedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT SUM(CAST(payout AS REAL)) FROM spectator_bets WHERE userId = :userId AND result = 'WON'")
    suspend fun getTotalSpectatorWinnings(userId: Long): Double?
}
