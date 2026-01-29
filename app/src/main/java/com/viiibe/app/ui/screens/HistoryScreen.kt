package com.viiibe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viiibe.app.data.model.Workout
import com.viiibe.app.ui.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    userViewModel: UserViewModel
) {
    val activeUser by userViewModel.activeUser.collectAsState()
    val userId = activeUser?.id ?: 0L

    // Get user-specific data
    val workouts by userViewModel.getWorkoutsForUser(userId).collectAsState(initial = emptyList())
    val totalWorkouts by userViewModel.getWorkoutCountForUser(userId).collectAsState(initial = 0)
    val totalMinutes by userViewModel.getTotalMinutesForUser(userId).collectAsState(initial = 0)
    val totalCalories by userViewModel.getTotalCaloriesForUser(userId).collectAsState(initial = 0)
    val totalDistance by userViewModel.getTotalDistanceForUser(userId).collectAsState(initial = 0f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Header
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "${activeUser?.name ?: "Your"}'s workout history",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Summary stats
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatItem(
                    value = totalWorkouts.toString(),
                    label = "Total Rides",
                    icon = Icons.Filled.DirectionsBike
                )
                SummaryStatItem(
                    value = "${(totalMinutes ?: 0) / 60}",
                    label = "Minutes",
                    icon = Icons.Filled.Timer
                )
                SummaryStatItem(
                    value = String.format("%.1f", totalDistance ?: 0f),
                    label = "Miles",
                    icon = Icons.Filled.Route
                )
                SummaryStatItem(
                    value = "${totalCalories ?: 0}",
                    label = "Calories",
                    icon = Icons.Filled.LocalFireDepartment
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Workout history list
        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No workouts yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Complete your first ride to see it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Text(
                text = "Recent Workouts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(workouts) { workout ->
                    WorkoutHistoryCard(workout = workout)
                }
            }
        }
    }
}

@Composable
fun SummaryStatItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WorkoutHistoryCard(workout: Workout) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Date and time header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = workout.videoTitle ?: "Free Ride",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${dateFormat.format(Date(workout.startTime))} at ${timeFormat.format(Date(workout.startTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDuration(workout.durationSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WorkoutMetricItem(
                    value = "${workout.totalOutput}",
                    label = "Output (kJ)",
                    icon = Icons.Filled.Bolt
                )
                WorkoutMetricItem(
                    value = "${workout.avgCadence}",
                    label = "Avg Cadence",
                    icon = Icons.Filled.Speed
                )
                WorkoutMetricItem(
                    value = String.format("%.1f", workout.totalDistance),
                    label = "Miles",
                    icon = Icons.Filled.Route
                )
                WorkoutMetricItem(
                    value = "${workout.caloriesBurned}",
                    label = "Calories",
                    icon = Icons.Filled.LocalFireDepartment
                )
                if (workout.avgHeartRate > 0) {
                    WorkoutMetricItem(
                        value = "${workout.avgHeartRate}",
                        label = "Avg HR",
                        icon = Icons.Filled.Favorite
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutMetricItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
