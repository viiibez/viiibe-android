package com.viiibe.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viiibe.app.arcade.data.AIDifficulty
import com.viiibe.app.arcade.data.ArcadeGame
import com.viiibe.app.arcade.data.ArcadeGameResult

/**
 * Room entity for storing arcade game results locally.
 * These are synced to the backend when a wallet is connected.
 */
@Entity(
    tableName = "arcade_games",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["synced"]),
        Index(value = ["playedAt"])
    ]
)
data class ArcadeGameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val gameType: String,
    val difficulty: String,
    val score: Int,
    val won: Boolean,
    val durationSeconds: Int,
    val details: String,  // JSON string of game-specific details
    val playedAt: Long,
    val synced: Boolean = false,
    val syncedAt: Long? = null
) {
    companion object {
        /**
         * Create an entity from an ArcadeGameResult
         */
        fun fromResult(userId: Long, result: ArcadeGameResult): ArcadeGameEntity {
            // Convert details map to JSON string
            val detailsJson = buildString {
                append("{")
                result.details.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) append(",")
                    append("\"$key\":")
                    when (value) {
                        is String -> append("\"$value\"")
                        is Number -> append(value)
                        is Boolean -> append(value)
                        else -> append("\"$value\"")
                    }
                }
                append("}")
            }

            return ArcadeGameEntity(
                userId = userId,
                gameType = result.game.name,
                difficulty = result.difficulty.name,
                score = result.score,
                won = result.won,
                durationSeconds = result.durationSeconds,
                details = detailsJson,
                playedAt = result.timestamp
            )
        }
    }

    /**
     * Convert back to ArcadeGameResult for display
     */
    fun toResult(): ArcadeGameResult {
        // Parse details JSON manually (simple implementation)
        val detailsMap = mutableMapOf<String, Any>()
        val cleanJson = details.trim().removePrefix("{").removeSuffix("}")
        if (cleanJson.isNotBlank()) {
            cleanJson.split(",").forEach { pair ->
                val (key, value) = pair.split(":").map { it.trim().trim('"') }
                detailsMap[key] = value
            }
        }

        return ArcadeGameResult(
            game = ArcadeGame.valueOf(gameType),
            timestamp = playedAt,
            durationSeconds = durationSeconds,
            score = score,
            won = won,
            difficulty = AIDifficulty.valueOf(difficulty),
            details = detailsMap
        )
    }
}

/**
 * DTO for syncing arcade games to the backend
 */
data class ArcadeGameSyncDto(
    val walletAddress: String,
    val gameType: String,
    val difficulty: String,
    val score: Int,
    val won: Boolean,
    val durationSeconds: Int,
    val details: Map<String, Any>,
    val playedAt: Long
) {
    companion object {
        fun fromEntity(entity: ArcadeGameEntity, walletAddress: String): ArcadeGameSyncDto {
            // Parse details JSON
            val detailsMap = mutableMapOf<String, Any>()
            val cleanJson = entity.details.trim().removePrefix("{").removeSuffix("}")
            if (cleanJson.isNotBlank()) {
                cleanJson.split(",").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        val key = parts[0].trim().trim('"')
                        val value = parts[1].trim().trim('"')
                        detailsMap[key] = value
                    }
                }
            }

            return ArcadeGameSyncDto(
                walletAddress = walletAddress,
                gameType = entity.gameType,
                difficulty = entity.difficulty,
                score = entity.score,
                won = entity.won,
                durationSeconds = entity.durationSeconds,
                details = detailsMap,
                playedAt = entity.playedAt
            )
        }
    }
}
