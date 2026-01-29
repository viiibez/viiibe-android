package com.viiibe.app.arcade.ui.games

import androidx.compose.animation.core.*
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
import androidx.compose.ui.unit.sp
import com.viiibe.app.arcade.data.*
import com.viiibe.app.data.model.RideMetrics
import kotlin.math.sin

@Composable
fun SprintRaceGame(
    state: SprintRaceState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Game canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSprintRaceGame(state)
        }

        // Score overlay
        ScoreOverlay(
            score = state.score,
            distance = state.distanceMeters,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp)
        )

        // Position indicator
        PositionIndicator(
            playerPosition = state.playerPosition,
            aiPositions = state.aiPlayers.map { it.position },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Boost indicator
        if (state.hasBoost) {
            BoostIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            )
        }

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Sprint Race",
                    instructions = "Pedal to move! Resistance UP/DOWN to change lanes.",
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

private fun DrawScope.drawSprintRaceGame(state: SprintRaceState) {
    val width = size.width
    val height = size.height

    // Draw parallax background
    drawParallaxBackground(state.playerPosition)

    // Draw road/track
    drawTrack(height)

    // Draw lane markers
    drawLaneMarkers(state.playerPosition, height)

    // Draw obstacles
    state.obstacles.forEach { obstacle ->
        if (!obstacle.passed) {
            drawObstacle(obstacle, state.playerPosition, height)
        }
    }

    // Draw AI racers
    state.aiPlayers.forEachIndexed { index, aiRacer ->
        drawRacer(
            position = aiRacer.position - state.playerPosition,
            lane = aiRacer.lane,
            color = getAIColor(index),
            height = height,
            isPlayer = false,
            name = aiRacer.player.name
        )
    }

    // Draw player (always at center horizontally)
    drawRacer(
        position = 0f,  // Player is always at visual center
        lane = state.playerLane,
        color = Color(0xFF4CAF50),
        height = height,
        isPlayer = true,
        hasBoost = state.hasBoost
    )
}

private fun DrawScope.drawParallaxBackground(playerPosition: Float) {
    val width = size.width
    val height = size.height

    // Sky gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F0F23),
                Color(0xFF1A1A2E),
                Color(0xFF16213E)
            )
        )
    )

    // Stars (parallax layer 1 - slowest)
    val starOffset = (playerPosition * 50) % width
    for (i in 0..30) {
        val x = ((i * 73 + starOffset) % width)
        val y = (i * 37 % (height * 0.4f)).toFloat()
        drawCircle(
            color = Color.White.copy(alpha = 0.5f + (i % 3) * 0.2f),
            radius = 1f + (i % 3),
            center = Offset(x, y)
        )
    }

    // Mountains (parallax layer 2)
    val mountainOffset = (playerPosition * 100) % width
    val mountainPath = Path().apply {
        moveTo(0f, height * 0.5f)
        for (x in 0..width.toInt() step 100) {
            val peakHeight = height * 0.3f + sin((x + mountainOffset) / 200.0).toFloat() * height * 0.1f
            lineTo(x.toFloat(), peakHeight)
            lineTo(x + 50f, height * 0.35f + sin((x + 50 + mountainOffset) / 150.0).toFloat() * height * 0.05f)
        }
        lineTo(width, height * 0.5f)
        close()
    }
    drawPath(mountainPath, Color(0xFF2D2D44))

    // Trees (parallax layer 3 - fastest)
    val treeOffset = (playerPosition * 200) % width
    for (i in 0..15) {
        val x = ((i * 120 + treeOffset) % (width + 100)) - 50
        drawTree(x, height * 0.48f)
    }
}

private fun DrawScope.drawTree(x: Float, baseY: Float) {
    // Trunk
    drawRect(
        color = Color(0xFF4A3728),
        topLeft = Offset(x - 5, baseY),
        size = Size(10f, 40f)
    )
    // Foliage
    val foliagePath = Path().apply {
        moveTo(x, baseY - 60)
        lineTo(x - 25, baseY)
        lineTo(x + 25, baseY)
        close()
    }
    drawPath(foliagePath, Color(0xFF1B4332))
}

private fun DrawScope.drawTrack(height: Float) {
    val trackTop = height * 0.55f
    val trackHeight = height * 0.4f

    // Track surface
    drawRect(
        color = Color(0xFF2C2C2C),
        topLeft = Offset(0f, trackTop),
        size = Size(size.width, trackHeight)
    )

    // Track edges
    drawRect(
        color = Color(0xFFFFFFFF),
        topLeft = Offset(0f, trackTop),
        size = Size(size.width, 4f)
    )
    drawRect(
        color = Color(0xFFFFFFFF),
        topLeft = Offset(0f, trackTop + trackHeight - 4),
        size = Size(size.width, 4f)
    )
}

private fun DrawScope.drawLaneMarkers(playerPosition: Float, height: Float) {
    val trackTop = height * 0.55f
    val laneHeight = height * 0.4f / 3f
    val markerOffset = (playerPosition * 500) % 100

    // Lane dividers (dashed)
    for (lane in 1..2) {
        val y = trackTop + laneHeight * lane
        for (x in -100..(size.width.toInt() + 100) step 100) {
            val xPos = x - markerOffset
            drawRect(
                color = Color.White.copy(alpha = 0.5f),
                topLeft = Offset(xPos, y - 2),
                size = Size(50f, 4f)
            )
        }
    }
}

private fun DrawScope.drawObstacle(obstacle: Obstacle, playerPosition: Float, height: Float) {
    val trackTop = height * 0.55f
    val laneHeight = height * 0.4f / 3f
    val laneCenter = trackTop + laneHeight * obstacle.lane + laneHeight / 2

    // Calculate x position relative to player
    val relativePosition = obstacle.position - playerPosition
    val screenX = size.width * 0.3f + relativePosition * size.width * 2

    // Only draw if on screen
    if (screenX < -50 || screenX > size.width + 50) return

    val obstacleSize = 40f

    when (obstacle.type) {
        ObstacleType.HURDLE -> {
            // Red hurdle - jump over
            drawRect(
                color = Color(0xFFE53935),
                topLeft = Offset(screenX - obstacleSize / 2, laneCenter - obstacleSize),
                size = Size(obstacleSize, obstacleSize * 0.6f)
            )
            // Posts
            drawRect(
                color = Color(0xFFB71C1C),
                topLeft = Offset(screenX - obstacleSize / 2, laneCenter - obstacleSize),
                size = Size(5f, obstacleSize)
            )
            drawRect(
                color = Color(0xFFB71C1C),
                topLeft = Offset(screenX + obstacleSize / 2 - 5, laneCenter - obstacleSize),
                size = Size(5f, obstacleSize)
            )
        }
        ObstacleType.PUDDLE -> {
            // Blue puddle - slide under/through
            drawOval(
                color = Color(0xFF2196F3).copy(alpha = 0.7f),
                topLeft = Offset(screenX - obstacleSize, laneCenter - obstacleSize / 4),
                size = Size(obstacleSize * 2, obstacleSize / 2)
            )
        }
        ObstacleType.CONE -> {
            // Orange cone - avoid
            val conePath = Path().apply {
                moveTo(screenX, laneCenter - obstacleSize)
                lineTo(screenX - obstacleSize / 2, laneCenter)
                lineTo(screenX + obstacleSize / 2, laneCenter)
                close()
            }
            drawPath(conePath, Color(0xFFFF9800))
        }
    }
}

private fun DrawScope.drawRacer(
    position: Float,
    lane: Int,
    color: Color,
    height: Float,
    isPlayer: Boolean,
    hasBoost: Boolean = false,
    name: String? = null
) {
    val trackTop = height * 0.55f
    val laneHeight = height * 0.4f / 3f
    val laneCenter = trackTop + laneHeight * lane + laneHeight / 2

    // Player is always at 30% from left, others are relative
    val screenX = if (isPlayer) {
        size.width * 0.3f
    } else {
        size.width * 0.3f + position * size.width * 2
    }

    // Only draw if on screen
    if (screenX < -50 || screenX > size.width + 50) return

    val bikeWidth = 60f
    val bikeHeight = 30f

    // Boost effect
    if (hasBoost) {
        // Flame trail
        for (i in 1..5) {
            drawCircle(
                color = Color(0xFFFF9800).copy(alpha = 0.3f - i * 0.05f),
                radius = 15f + i * 5,
                center = Offset(screenX - bikeWidth / 2 - i * 15, laneCenter)
            )
        }
    }

    // Bike body
    drawRoundRect(
        color = color,
        topLeft = Offset(screenX - bikeWidth / 2, laneCenter - bikeHeight / 2),
        size = Size(bikeWidth, bikeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )

    // Wheels
    drawCircle(
        color = Color.DarkGray,
        radius = 12f,
        center = Offset(screenX - bikeWidth / 3, laneCenter + bikeHeight / 4)
    )
    drawCircle(
        color = Color.DarkGray,
        radius = 12f,
        center = Offset(screenX + bikeWidth / 3, laneCenter + bikeHeight / 4)
    )

    // Rider silhouette
    drawCircle(
        color = color.copy(alpha = 0.8f),
        radius = 10f,
        center = Offset(screenX, laneCenter - bikeHeight / 2 - 15)
    )

    // Name tag for AI
    if (name != null && !isPlayer) {
        // Simple name indicator above racer
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = 20f,
            center = Offset(screenX, laneCenter - bikeHeight - 30)
        )
    }
}

private fun getAIColor(index: Int): Color {
    return when (index % 4) {
        0 -> Color(0xFFF44336)  // Red
        1 -> Color(0xFF2196F3)  // Blue
        2 -> Color(0xFFFF9800)  // Orange
        else -> Color(0xFF9C27B0)  // Purple
    }
}

@Composable
private fun ScoreOverlay(
    score: Int,
    distance: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "SCORE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${distance.toInt()}m",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PositionIndicator(
    playerPosition: Float,
    aiPositions: List<Float>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "POSITIONS",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Mini track with position dots
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Color(0xFF2C2C2C), shape = MaterialTheme.shapes.small)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxPos = maxOf(playerPosition, aiPositions.maxOrNull() ?: 0f) + 0.1f

                    // Player dot
                    val playerX = (playerPosition / maxPos * size.width).coerceIn(10f, size.width - 10f)
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = 8f,
                        center = Offset(playerX, size.height / 2)
                    )

                    // AI dots
                    aiPositions.forEachIndexed { index, pos ->
                        val x = (pos / maxPos * size.width).coerceIn(10f, size.width - 10f)
                        drawCircle(
                            color = getAIColor(index),
                            radius = 6f,
                            center = Offset(x, size.height / 2)
                        )
                    }
                }
            }

            // Position text
            val position = 1 + aiPositions.count { it > playerPosition }
            Text(
                text = "${position}${getOrdinalSuffix(position)} place",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (position == 1) Color(0xFFFFD700) else Color.White,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun getOrdinalSuffix(n: Int): String {
    return when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}

@Composable
private fun BoostIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "boost")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boost_alpha"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = alpha)
        )
    ) {
        Text(
            text = "BOOST!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun StartOverlay(
    gameName: String,
    instructions: String,
    onStart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onStart,
                    modifier = Modifier.size(width = 200.dp, height = 56.dp)
                ) {
                    Text(
                        text = "START PEDALING",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Or just start pedaling to begin!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CountdownOverlay(count: Int) {
    val scale by animateFloatAsState(
        targetValue = if (count > 0) 1.5f else 1f,
        animationSpec = tween(300),
        label = "countdown_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 0) count.toString() else "GO!",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = (100 * scale).sp
            ),
            fontWeight = FontWeight.Bold,
            color = if (count > 0) Color.White else Color(0xFF4CAF50)
        )
    }
}
