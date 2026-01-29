package com.viiibe.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User profile for multi-user support
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarColor: Int = 0, // Color index for avatar
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false, // Currently selected user
    // User-specific settings
    val useMetricUnits: Boolean = false,
    val showHeartRateZones: Boolean = true,
    val maxHeartRate: Int = 220, // For HR zone calculations
    val ftpWatts: Int = 200 // Functional Threshold Power for power zones
)

/**
 * Workout linked to a specific user
 */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class Workout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long, // Link to user profile
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Int = 0,
    val totalOutput: Int = 0, // kJ
    val avgCadence: Int = 0,
    val avgResistance: Int = 0,
    val avgSpeed: Float = 0f, // mph
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val maxCadence: Int = 0,
    val maxOutput: Int = 0,
    val totalDistance: Float = 0f, // miles
    val caloriesBurned: Int = 0,
    val workoutType: String = "free_ride",
    val videoTitle: String? = null,
    val videoUrl: String? = null,
    val synced: Boolean = false, // Whether synced to backend
    val syncedAt: Long? = null
)

@Entity(tableName = "workout_samples")
data class WorkoutSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val timestamp: Long,
    val cadence: Int,
    val resistance: Int,
    val power: Int, // watts
    val speed: Float,
    val heartRate: Int,
    val distance: Float
)

data class RideMetrics(
    val cadence: Int = 0,
    val resistance: Int = 0,
    val power: Int = 0, // watts (instantaneous output)
    val speed: Float = 0f, // mph
    val heartRate: Int = 0,
    val distance: Float = 0f, // miles
    val totalOutput: Int = 0, // kJ (cumulative)
    val calories: Int = 0,
    val elapsedSeconds: Int = 0
)

data class BikeConnectionState(
    val isConnected: Boolean = false,
    val isScanning: Boolean = false,
    val deviceName: String? = null,
    val batteryLevel: Int? = null,
    val error: String? = null
)

data class WorkoutVideo(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val duration: String,
    val instructor: String,
    val difficulty: String, // beginner, intermediate, advanced
    val category: String, // hiit, climb, intervals, endurance, scenic
    val isFavorite: Boolean = false
)

data class WorkoutSummary(
    val totalWorkouts: Int,
    val totalMinutes: Int,
    val totalOutput: Int,
    val totalCalories: Int,
    val totalDistance: Float,
    val avgOutput: Int,
    val avgCadence: Int
)

/**
 * Avatar colors for user profiles
 */
object AvatarColors {
    val colors = listOf(
        0xFFE53935.toInt(), // Red
        0xFFD81B60.toInt(), // Pink
        0xFF8E24AA.toInt(), // Purple
        0xFF5E35B1.toInt(), // Deep Purple
        0xFF3949AB.toInt(), // Indigo
        0xFF1E88E5.toInt(), // Blue
        0xFF039BE5.toInt(), // Light Blue
        0xFF00ACC1.toInt(), // Cyan
        0xFF00897B.toInt(), // Teal
        0xFF43A047.toInt(), // Green
        0xFF7CB342.toInt(), // Light Green
        0xFFFDD835.toInt(), // Yellow
        0xFFFFB300.toInt(), // Amber
        0xFFFB8C00.toInt(), // Orange
        0xFFF4511E.toInt(), // Deep Orange
    )

    fun getColor(index: Int): Int = colors[index % colors.size]
}
