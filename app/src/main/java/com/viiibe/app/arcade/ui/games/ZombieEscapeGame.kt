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
import com.viiibe.app.arcade.data.*
import com.viiibe.app.data.model.RideMetrics
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ZombieEscapeGame(
    state: ZombieEscapeState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    // Heartbeat animation that intensifies when zombies are close
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartbeatScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f + state.heartbeatIntensity * 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween((600 - state.heartbeatIntensity * 400).toInt()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat_scale"
    )

    // Screen shake when zombies are very close
    val shakeOffset = if (state.heartbeatIntensity > 0.7f) {
        Offset(
            (Random.nextFloat() - 0.5f) * 10f * state.heartbeatIntensity,
            (Random.nextFloat() - 0.5f) * 10f * state.heartbeatIntensity
        )
    } else Offset.Zero

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(x = shakeOffset.x.dp, y = shakeOffset.y.dp)
            .background(Color(0xFF1A1A1A))
    ) {
        // Game canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawZombieEscapeGame(state, heartbeatScale)
        }

        // Distance indicator
        DistanceIndicator(
            playerDistance = state.playerDistance,
            zombieDistance = state.zombieDistance,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp)
        )

        // Speed boost indicator
        if (state.hasSpeedBoost) {
            SpeedBoostIndicator(
                timeRemaining = state.speedBoostTimeMs,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            )
        }

        // Warning overlay when zombies are close
        if (state.heartbeatIntensity > 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = state.heartbeatIntensity * 0.2f))
            )
        }

        // Score
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 60.dp, start = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("SCORE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Text(
                    text = state.score.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Zombie Escape",
                    instructions = "PEDAL TO SURVIVE! Keep cadence above 60 RPM to outrun the zombies!",
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

private fun DrawScope.drawZombieEscapeGame(state: ZombieEscapeState, heartbeatScale: Float) {
    val width = size.width
    val height = size.height

    // Draw spooky background
    drawSpookyBackground(state)

    // Draw road/path
    drawEscapeRoad(state.playerDistance)

    // Draw power-ups
    state.powerUps.filter { !it.collected }.forEach { powerUp ->
        drawPowerUp(powerUp, state.playerDistance)
    }

    // Draw player (cyclist)
    drawPlayer(height, state.playerSpeed, state.hasSpeedBoost)

    // Draw zombies chasing
    drawZombies(state, heartbeatScale)

    // Draw fog effect
    drawFog(state.heartbeatIntensity)
}

private fun DrawScope.drawSpookyBackground(state: ZombieEscapeState) {
    // Dark gradient sky
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0D0D0D),
                Color(0xFF1A1A2E),
                Color(0xFF2D1B4E)
            )
        )
    )

    // Moon
    val moonX = size.width * 0.8f
    val moonY = size.height * 0.15f
    drawCircle(
        color = Color(0xFFE0E0E0),
        radius = 50f,
        center = Offset(moonX, moonY)
    )
    // Moon crater shadows
    drawCircle(
        color = Color(0xFFBDBDBD),
        radius = 10f,
        center = Offset(moonX - 15, moonY + 10)
    )
    drawCircle(
        color = Color(0xFFBDBDBD),
        radius = 6f,
        center = Offset(moonX + 20, moonY - 5)
    )

    // Dead trees
    val treeOffset = (state.playerDistance * 50) % size.width
    for (i in 0..5) {
        val x = ((i * 200 + treeOffset) % (size.width + 100)) - 50
        drawDeadTree(x, size.height * 0.45f)
    }

    // Gravestones
    val graveOffset = (state.playerDistance * 80) % size.width
    for (i in 0..3) {
        val x = ((i * 300 + graveOffset) % (size.width + 100)) - 50
        drawGravestone(x, size.height * 0.52f)
    }
}

private fun DrawScope.drawDeadTree(x: Float, baseY: Float) {
    // Trunk
    drawRect(
        color = Color(0xFF2D2D2D),
        topLeft = Offset(x - 8, baseY - 100),
        size = Size(16f, 100f)
    )
    // Branches
    drawLine(Color(0xFF2D2D2D), Offset(x, baseY - 80), Offset(x - 40, baseY - 120), strokeWidth = 6f)
    drawLine(Color(0xFF2D2D2D), Offset(x, baseY - 60), Offset(x + 35, baseY - 90), strokeWidth = 5f)
    drawLine(Color(0xFF2D2D2D), Offset(x, baseY - 40), Offset(x - 25, baseY - 60), strokeWidth = 4f)
}

private fun DrawScope.drawGravestone(x: Float, baseY: Float) {
    // Stone
    val path = Path().apply {
        moveTo(x - 15, baseY)
        lineTo(x - 15, baseY - 30)
        quadraticBezierTo(x - 15f, baseY - 45f, x.toFloat(), baseY - 45f)
        quadraticBezierTo(x + 15f, baseY - 45f, x + 15f, baseY - 30f)
        lineTo(x + 15, baseY)
        close()
    }
    drawPath(path, Color(0xFF424242))
}

private fun DrawScope.drawEscapeRoad(playerDistance: Float) {
    val roadTop = size.height * 0.55f
    val roadHeight = size.height * 0.45f

    // Road surface
    drawRect(
        color = Color(0xFF2C2C2C),
        topLeft = Offset(0f, roadTop),
        size = Size(size.width, roadHeight)
    )

    // Road markings
    val markingOffset = (playerDistance * 300) % 100
    for (x in -100..(size.width.toInt() + 100) step 100) {
        val xPos = x - markingOffset
        drawRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(xPos, roadTop + roadHeight / 2 - 3),
            size = Size(50f, 6f)
        )
    }
}

private fun DrawScope.drawPowerUp(powerUp: PowerUp, playerDistance: Float) {
    val relativePosition = powerUp.position - playerDistance
    val screenX = size.width * 0.3f + relativePosition * 5

    if (screenX < -50 || screenX > size.width + 50) return

    val y = size.height * 0.7f
    val color = when (powerUp.type) {
        PowerUpType.SPEED_BOOST -> Color(0xFF2196F3)
        PowerUpType.ZOMBIE_SLOW -> Color(0xFF9C27B0)
        PowerUpType.SCORE_BONUS -> Color(0xFFFFD700)
    }

    // Glow
    drawCircle(color.copy(alpha = 0.3f), radius = 35f, center = Offset(screenX, y))
    // Core
    drawCircle(color, radius = 20f, center = Offset(screenX, y))
    // Shine
    drawCircle(Color.White, radius = 8f, center = Offset(screenX - 5, y - 5))
}

private fun DrawScope.drawPlayer(height: Float, speed: Float, hasBoost: Boolean) {
    val playerX = size.width * 0.3f
    val playerY = height * 0.68f

    // Speed lines when boosting
    if (hasBoost) {
        for (i in 1..5) {
            drawLine(
                color = Color(0xFF2196F3).copy(alpha = 0.5f - i * 0.1f),
                start = Offset(playerX - 30 - i * 20, playerY - 10 + i * 5),
                end = Offset(playerX - 50 - i * 30, playerY - 10 + i * 5),
                strokeWidth = 4f
            )
        }
    }

    // Bike
    val bikeColor = if (hasBoost) Color(0xFF2196F3) else Color(0xFF4CAF50)

    // Wheels
    drawCircle(Color.DarkGray, radius = 18f, center = Offset(playerX - 25, playerY + 15))
    drawCircle(Color.DarkGray, radius = 18f, center = Offset(playerX + 25, playerY + 15))

    // Frame
    drawLine(bikeColor, Offset(playerX - 25, playerY + 15), Offset(playerX, playerY - 10), strokeWidth = 5f)
    drawLine(bikeColor, Offset(playerX + 25, playerY + 15), Offset(playerX, playerY - 10), strokeWidth = 5f)
    drawLine(bikeColor, Offset(playerX - 25, playerY + 15), Offset(playerX + 10, playerY), strokeWidth = 5f)

    // Rider
    drawCircle(Color(0xFFFFCCBC), radius = 12f, center = Offset(playerX, playerY - 25))
    drawLine(Color(0xFF1565C0), Offset(playerX, playerY - 15), Offset(playerX, playerY), strokeWidth = 8f)

    // Pedaling animation based on speed
    val pedalAngle = (System.currentTimeMillis() / (100 - speed.coerceIn(0f, 80f)).toLong()) % 360
}

private fun DrawScope.drawZombies(state: ZombieEscapeState, heartbeatScale: Float) {
    val gap = state.playerDistance - state.zombieDistance
    val zombieScreenX = size.width * 0.3f - gap * 5

    if (zombieScreenX > -100) {
        for (i in 0 until state.zombieCount) {
            val offsetX = i * 30f - (state.zombieCount * 15)
            val offsetY = sin(System.currentTimeMillis() / 200.0 + i).toFloat() * 10
            drawZombie(
                zombieScreenX + offsetX,
                size.height * 0.65f + offsetY,
                heartbeatScale
            )
        }
    }
}

private fun DrawScope.drawZombie(x: Float, y: Float, scale: Float) {
    val zombieScale = scale * 0.8f

    // Body
    drawRoundRect(
        color = Color(0xFF4A7C59),
        topLeft = Offset(x - 15 * zombieScale, y - 20 * zombieScale),
        size = Size(30 * zombieScale, 40 * zombieScale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f)
    )

    // Head
    drawCircle(
        color = Color(0xFF5D8A66),
        radius = 15 * zombieScale,
        center = Offset(x, y - 35 * zombieScale)
    )

    // Eyes (glowing red)
    drawCircle(Color.Red, radius = 4 * zombieScale, center = Offset(x - 6, y - 38 * zombieScale))
    drawCircle(Color.Red, radius = 4 * zombieScale, center = Offset(x + 6, y - 38 * zombieScale))

    // Arms reaching forward
    drawLine(
        Color(0xFF4A7C59),
        Offset(x + 15 * zombieScale, y - 10 * zombieScale),
        Offset(x + 40 * zombieScale, y - 20 * zombieScale),
        strokeWidth = 8 * zombieScale
    )
    drawLine(
        Color(0xFF4A7C59),
        Offset(x + 15 * zombieScale, y),
        Offset(x + 35 * zombieScale, y - 5 * zombieScale),
        strokeWidth = 8 * zombieScale
    )
}

private fun DrawScope.drawFog(intensity: Float) {
    if (intensity > 0.3f) {
        val alpha = (intensity - 0.3f) * 0.3f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF1A1A1A).copy(alpha = alpha),
                    Color(0xFF1A1A1A).copy(alpha = alpha * 1.5f)
                )
            )
        )
    }
}

@Composable
private fun DistanceIndicator(
    playerDistance: Float,
    zombieDistance: Float,
    modifier: Modifier = Modifier
) {
    val gap = (playerDistance - zombieDistance).toInt()
    val dangerColor = when {
        gap < 10 -> Color.Red
        gap < 20 -> Color(0xFFFF9800)
        gap < 30 -> Color.Yellow
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "ZOMBIE DISTANCE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "${gap}m",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = dangerColor
            )
            Text(
                text = if (gap < 15) "TOO CLOSE!" else if (gap < 25) "PEDAL FASTER!" else "SAFE",
                style = MaterialTheme.typography.labelMedium,
                color = dangerColor
            )
        }
    }
}

@Composable
private fun SpeedBoostIndicator(
    timeRemaining: Long,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "boost_pulse")
    val alpha by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(200), RepeatMode.Reverse),
        label = "boost_alpha"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = alpha))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BOOST!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${(timeRemaining / 1000)}s",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
    }
}
