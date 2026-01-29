package com.viiibe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viiibe.app.data.model.RideMetrics

@Composable
fun BottomMetricsBar(
    metrics: RideMetrics,
    elapsedSeconds: Int,
    isRiding: Boolean,
    isPaused: Boolean,
    useMetricUnits: Boolean,
    onStartRide: () -> Unit,
    onPauseResume: () -> Unit,
    onEndRide: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Metrics row
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Time
                MetricItem(
                    icon = Icons.Filled.Timer,
                    value = formatTime(elapsedSeconds),
                    label = "TIME",
                    color = Color.White
                )

                // Power
                MetricItem(
                    icon = Icons.Filled.Bolt,
                    value = "${metrics.power}",
                    label = "WATTS",
                    color = Color(0xFFFFB300) // Amber
                )

                // Cadence
                MetricItem(
                    icon = Icons.Filled.Speed,
                    value = "${metrics.cadence}",
                    label = "RPM",
                    color = Color(0xFF4FC3F7) // Light Blue
                )

                // Resistance
                MetricItem(
                    icon = Icons.Filled.Tune,
                    value = "${metrics.resistance}",
                    label = "RES %",
                    color = Color(0xFF81C784) // Light Green
                )

                // Heart Rate
                MetricItem(
                    icon = Icons.Filled.Favorite,
                    value = if (metrics.heartRate > 0) "${metrics.heartRate}" else "--",
                    label = "BPM",
                    color = Color(0xFFE57373) // Light Red
                )

                // Calories
                MetricItem(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "${metrics.calories}",
                    label = "KCAL",
                    color = Color(0xFFFF8A65) // Deep Orange
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                if (!isRiding) {
                    // Start button
                    FilledIconButton(
                        onClick = onStartRide,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Start Ride",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    // Pause/Resume button
                    FilledIconButton(
                        onClick = onPauseResume,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // End button
                    FilledIconButton(
                        onClick = onEndRide,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFF44336)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "End Ride",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Minimize button
                    FilledIconButton(
                        onClick = onMinimize,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureInPicture,
                            contentDescription = "Minimize",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 22.sp
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
