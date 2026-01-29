package com.viiibe.app.arcade.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viiibe.app.arcade.data.*
import com.viiibe.app.data.model.RideMetrics
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HillClimbGame(
    state: HillClimbState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF87CEEB),  // Sky blue
                        Color(0xFFB0E0E6),
                        Color(0xFFE0F7FA)
                    )
                )
            )
    ) {
        // Game canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHillClimbGame(state)
        }

        // Altitude meter (left side)
        AltitudeMeter(
            currentAltitude = state.altitude,
            targetAltitude = state.targetAltitude,
            checkpoints = state.checkpoints,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        )

        // Stats panel (top right)
        StatsPanel(
            grade = state.grade,
            speed = state.speed,
            fatigue = state.fatigueLevel,
            score = state.score,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp)
        )

        // Current checkpoint indicator
        state.checkpoints.getOrNull(state.nextCheckpoint)?.let { nextCheckpoint ->
            if (!nextCheckpoint.reached) {
                NextCheckpointIndicator(
                    checkpoint = nextCheckpoint,
                    currentAltitude = state.altitude,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Hill Climb",
                    instructions = "Climb to the summit! Maintain 60+ RPM to make progress. Too slow = roll back!",
                    onStart = onStartGame
                )
            }
            GamePhase.COUNTDOWN -> {
                CountdownOverlay(count = state.gameState.countdownValue)
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawHillClimbGame(state: HillClimbState) {
    val width = size.width
    val height = size.height

    // Draw mountain layers (parallax)
    drawMountainLayers(state.altitude, state.targetAltitude)

    // Draw the hill/road
    drawClimbingRoad(state)

    // Draw cyclist
    drawClimber(state)

    // Draw snow at high altitude
    if (state.altitude > state.targetAltitude * 0.7f) {
        drawSnow(state.altitude)
    }
}

private fun DrawScope.drawMountainLayers(altitude: Float, targetAltitude: Float) {
    val progress = altitude / targetAltitude
    val width = size.width
    val height = size.height

    // Far mountains (move slowly)
    val farMountainColor = Color(0xFF7986CB).copy(alpha = 0.5f)
    val farPath = Path().apply {
        moveTo(0f, height * 0.4f)
        val offset = progress * 100
        for (x in 0..width.toInt() step 150) {
            val peakHeight = height * (0.2f + sin((x + offset) / 300.0).toFloat() * 0.15f)
            lineTo(x.toFloat(), peakHeight)
            lineTo(x + 75f, height * (0.25f + cos((x + 75 + offset) / 250.0).toFloat() * 0.1f))
        }
        lineTo(width, height * 0.5f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(farPath, farMountainColor)

    // Mid mountains
    val midMountainColor = Color(0xFF5C6BC0).copy(alpha = 0.7f)
    val midPath = Path().apply {
        moveTo(0f, height * 0.5f)
        val offset = progress * 200
        for (x in 0..width.toInt() step 120) {
            val peakHeight = height * (0.3f + sin((x + offset) / 200.0).toFloat() * 0.1f)
            lineTo(x.toFloat(), peakHeight)
            lineTo(x + 60f, height * (0.35f + cos((x + 60 + offset) / 180.0).toFloat() * 0.08f))
        }
        lineTo(width, height * 0.55f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(midPath, midMountainColor)

    // Clouds
    val cloudOffset = (altitude * 2) % width
    for (i in 0..3) {
        val cloudX = ((i * 300 + cloudOffset) % (width + 200)) - 100
        val cloudY = height * (0.1f + i * 0.05f)
        drawCloud(cloudX, cloudY)
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float) {
    val cloudColor = Color.White.copy(alpha = 0.8f)
    drawCircle(cloudColor, radius = 30f, center = Offset(x, y))
    drawCircle(cloudColor, radius = 25f, center = Offset(x - 25, y + 5))
    drawCircle(cloudColor, radius = 25f, center = Offset(x + 25, y + 5))
    drawCircle(cloudColor, radius = 20f, center = Offset(x - 40, y + 10))
    drawCircle(cloudColor, radius = 20f, center = Offset(x + 40, y + 10))
}

private fun DrawScope.drawClimbingRoad(state: HillClimbState) {
    val width = size.width
    val height = size.height

    // Calculate road angle based on grade
    val gradeAngle = (state.grade / 100f * 30f).coerceIn(5f, 25f)  // 5-25 degree visual angle

    // Ground/hill
    val hillColor = when {
        state.altitude > state.targetAltitude * 0.8f -> Color(0xFF90A4AE)  // Rocky
        state.altitude > state.targetAltitude * 0.5f -> Color(0xFF8D6E63)  // Brown dirt
        else -> Color(0xFF66BB6A)  // Green grass
    }

    val hillPath = Path().apply {
        moveTo(0f, height * 0.55f)
        // Sloped line representing the hill
        lineTo(width, height * (0.55f - gradeAngle / 100f))
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(hillPath, hillColor)

    // Road surface
    val roadPath = Path().apply {
        moveTo(0f, height * 0.7f)
        lineTo(width, height * (0.7f - gradeAngle / 100f * 0.5f))
        lineTo(width, height * (0.7f - gradeAngle / 100f * 0.5f) + 80)
        lineTo(0f, height * 0.7f + 80)
        close()
    }
    drawPath(roadPath, Color(0xFF424242))

    // Road center line
    val centerY = height * 0.7f + 40
    val endY = height * (0.7f - gradeAngle / 100f * 0.5f) + 40
    drawLine(
        color = Color.Yellow.copy(alpha = 0.7f),
        start = Offset(0f, centerY),
        end = Offset(width, endY),
        strokeWidth = 4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f), phase = (state.altitude * 10) % 50)
    )

    // Grade indicator on road
    val gradeText = "${state.grade.toInt()}%"
    // (Can't draw text in Canvas easily, shown in UI overlay instead)
}

private fun DrawScope.drawClimber(state: HillClimbState) {
    val width = size.width
    val height = size.height
    val playerX = width * 0.35f
    val gradeOffset = (state.grade / 100f * 20f)
    val playerY = height * 0.7f + 20 - gradeOffset

    // Wheel rotation based on movement
    val wheelRotation = (state.altitude * 50) % 360

    // Bike frame tilt based on grade
    val tiltAngle = -state.grade / 100f * 0.3f

    // Bike body
    val bikeColor = Color(0xFFE53935)

    // Back wheel
    drawCircle(Color.DarkGray, radius = 22f, center = Offset(playerX - 35, playerY + 25))
    drawCircle(Color(0xFF424242), radius = 18f, center = Offset(playerX - 35, playerY + 25))

    // Front wheel
    drawCircle(Color.DarkGray, radius = 22f, center = Offset(playerX + 35, playerY + 15))
    drawCircle(Color(0xFF424242), radius = 18f, center = Offset(playerX + 35, playerY + 15))

    // Frame
    drawLine(bikeColor, Offset(playerX - 35, playerY + 25), Offset(playerX, playerY - 5), strokeWidth = 6f)
    drawLine(bikeColor, Offset(playerX + 35, playerY + 15), Offset(playerX + 10, playerY - 10), strokeWidth = 6f)
    drawLine(bikeColor, Offset(playerX - 35, playerY + 25), Offset(playerX + 10, playerY + 5), strokeWidth = 6f)
    drawLine(bikeColor, Offset(playerX, playerY - 5), Offset(playerX + 10, playerY - 10), strokeWidth = 6f)

    // Handlebars
    drawLine(bikeColor, Offset(playerX + 10, playerY - 10), Offset(playerX + 20, playerY - 20), strokeWidth = 5f)

    // Rider
    // Body
    drawLine(Color(0xFF1565C0), Offset(playerX, playerY - 5), Offset(playerX - 5, playerY - 35), strokeWidth = 10f)
    // Head
    drawCircle(Color(0xFFFFCCBC), radius = 14f, center = Offset(playerX, playerY - 50))
    // Helmet
    val helmetPath = Path().apply {
        moveTo(playerX - 15, playerY - 50)
        quadraticBezierTo(playerX.toFloat(), playerY - 70, playerX + 15, playerY - 50)
    }
    drawPath(helmetPath, Color(0xFFE53935), style = Stroke(width = 6f))

    // Arms reaching for handlebars
    drawLine(Color(0xFFFFCCBC), Offset(playerX - 5, playerY - 30), Offset(playerX + 15, playerY - 20), strokeWidth = 6f)

    // Effort indicator (sweat drops if fatigued)
    if (state.fatigueLevel > 0.3f) {
        val dropCount = (state.fatigueLevel * 3).toInt()
        for (i in 0 until dropCount) {
            val dropX = playerX + 15 + i * 8
            val dropY = playerY - 55 + (System.currentTimeMillis() / 100 + i * 20) % 30
            drawCircle(Color(0xFF64B5F6), radius = 3f, center = Offset(dropX, dropY.toFloat()))
        }
    }

    // Speed lines when going fast
    if (state.speed > 5f) {
        for (i in 1..3) {
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(playerX - 60 - i * 20, playerY + i * 10),
                end = Offset(playerX - 80 - i * 25, playerY + i * 10),
                strokeWidth = 2f
            )
        }
    }
}

private fun DrawScope.drawSnow(altitude: Float) {
    // Snow particles
    val snowIntensity = ((altitude / 1000f) - 0.7f).coerceIn(0f, 0.3f)
    val snowCount = (snowIntensity * 50).toInt()

    for (i in 0 until snowCount) {
        val x = (System.currentTimeMillis() / 50 + i * 73) % size.width.toLong()
        val y = (System.currentTimeMillis() / 30 + i * 47) % size.height.toLong()
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = 3f,
            center = Offset(x.toFloat(), y.toFloat())
        )
    }
}

@Composable
private fun AltitudeMeter(
    currentAltitude: Float,
    targetAltitude: Float,
    checkpoints: List<Checkpoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(80.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SUMMIT",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "${targetAltitude.toInt()}m",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFD700)
            )

            // Altitude bar
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            ) {
                // Progress fill
                val progress = (currentAltitude / targetAltitude).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progress)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50),
                                    Color(0xFF8BC34A),
                                    Color(0xFFCDDC39)
                                )
                            )
                        )
                )

                // Checkpoint markers
                checkpoints.forEach { checkpoint ->
                    val checkpointProgress = checkpoint.altitude / targetAltitude
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .offset(y = (-checkpointProgress * 100).dp)  // Approximate
                            .background(
                                if (checkpoint.reached) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }

            Text(
                text = "${currentAltitude.toInt()}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun StatsPanel(
    grade: Float,
    speed: Float,
    fatigue: Float,
    score: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Grade
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GRADE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${grade.toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        grade > 12 -> Color.Red
                        grade > 8 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SPEED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${speed.toInt()} m/s",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (speed < 0) Color.Red else Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fatigue bar
            Text("FATIGUE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(8.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fatigue)
                        .fillMaxHeight()
                        .background(
                            when {
                                fatigue > 0.7f -> Color.Red
                                fatigue > 0.4f -> Color(0xFFFF9800)
                                else -> Color(0xFF4CAF50)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Score
            Text("SCORE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
private fun NextCheckpointIndicator(
    checkpoint: Checkpoint,
    currentAltitude: Float,
    modifier: Modifier = Modifier
) {
    val remaining = (checkpoint.altitude - currentAltitude).toInt()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NEXT: ${checkpoint.name}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "${remaining}m to go",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }
    }
}
