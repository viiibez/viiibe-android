package com.viiibe.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Player profile data synced from backend.
 * Combines X (Twitter) profile info with game statistics.
 */
data class PlayerProfile(
    val walletAddress: String,
    val xUsername: String?,
    val xProfilePicture: String?,
    val totalGames: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Float,
    val totalEarnings: Double,
    val totalWagered: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val favoriteGame: String?,
    val rank: Int?,
    val joinedAt: String?
)

/**
 * Backend API response for player stats
 */
data class PlayerStatsResponse(
    val success: Boolean,
    val data: PlayerStatsData?,
    val error: String?
)

data class PlayerStatsData(
    val address: String,
    val name: String,
    val totalGames: Int,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val totalWagered: String,
    val totalWon: String,
    val totalLost: String,
    val winRate: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val favoriteGame: String?,
    val lastActive: Long,
    val createdAt: Long
)

/**
 * Backend API response for auth profile
 */
data class AuthProfileResponse(
    val success: Boolean,
    val data: AuthProfileData?
)

data class AuthProfileData(
    val walletAddress: String,
    val xId: String?,
    val xUsername: String?,
    val xProfilePicture: String?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val registered: Boolean
)

/**
 * Game history item from backend
 */
data class GameHistoryItem(
    val id: String,
    val gameType: String,
    val gameMode: String,
    val hostAddress: String,
    val guestAddress: String?,
    val hostScore: Int,
    val guestScore: Int?,
    val winnerAddress: String?,
    val stakeAmount: Double?,
    val durationMs: Long?,
    val startedAt: Long?,
    val endedAt: Long?,
    val createdAt: Long
)

data class GameHistoryResponse(
    val success: Boolean,
    val data: List<GameHistoryItem>,
    val count: Int
)

/**
 * Room entity for caching player profile locally
 */
@Entity(
    tableName = "player_profiles",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["walletAddress"])]
)
data class CachedPlayerProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val walletAddress: String,
    val xUsername: String?,
    val xProfilePicture: String?,
    val totalGames: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Float,
    val totalEarnings: Double,
    val totalWagered: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val favoriteGame: String?,
    val rank: Int?,
    val joinedAt: String?,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val profilePictureRefreshedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for caching game history locally
 */
@Entity(
    tableName = "game_history",
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
data class CachedGameHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val gameId: String,
    val gameType: String,
    val gameMode: String,
    val hostAddress: String,
    val guestAddress: String?,
    val hostScore: Int,
    val guestScore: Int?,
    val winnerAddress: String?,
    val stakeAmount: Double?,
    val durationMs: Long?,
    val startedAt: Long?,
    val endedAt: Long?,
    val createdAt: Long
)

/**
 * Extension function to convert cached profile to PlayerProfile
 */
fun CachedPlayerProfile.toPlayerProfile(): PlayerProfile {
    return PlayerProfile(
        walletAddress = walletAddress,
        xUsername = xUsername,
        xProfilePicture = xProfilePicture,
        totalGames = totalGames,
        wins = wins,
        losses = losses,
        winRate = winRate,
        totalEarnings = totalEarnings,
        totalWagered = totalWagered,
        currentStreak = currentStreak,
        bestStreak = bestStreak,
        favoriteGame = favoriteGame,
        rank = rank,
        joinedAt = joinedAt
    )
}

/**
 * Extension function to convert game history item to cached version
 */
fun GameHistoryItem.toCached(userId: Long): CachedGameHistory {
    return CachedGameHistory(
        userId = userId,
        gameId = id,
        gameType = gameType,
        gameMode = gameMode,
        hostAddress = hostAddress,
        guestAddress = guestAddress,
        hostScore = hostScore,
        guestScore = guestScore,
        winnerAddress = winnerAddress,
        stakeAmount = stakeAmount,
        durationMs = durationMs,
        startedAt = startedAt,
        endedAt = endedAt,
        createdAt = createdAt
    )
}
