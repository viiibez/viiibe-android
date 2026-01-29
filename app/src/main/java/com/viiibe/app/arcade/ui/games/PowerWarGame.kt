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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PowerWarGame(
    state: PowerWarState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        // Game canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPowerWarGame(state)
        }

        // Round indicator
        RoundIndicator(
            round = state.round,
            playerWins = state.playerRoundsWon,
            aiWins = state.aiRoundsWon,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        )

        // Power meters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PowerMeter(
                label = "YOU",
                power = state.playerPower,
                fatigue = state.playerFatigue,
                isAnchored = state.playerAnchored,
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(100.dp))

            PowerMeter(
                label = state.aiPlayer?.name ?: "AI",
                power = state.aiPower,
                fatigue = state.aiFatigue,
                isAnchored = state.aiAnchored,
                color = Color(0xFFF44336),
                modifier = Modifier.weight(1f)
            )
        }

        // Instructions overlay
        if (state.playerAnchored) {
            AnchorIndicator(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
            )
        }

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Power War",
                    instructions = "Out-power your opponent! High resistance = Anchor mode (blocks but can't pull)",
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

private fun DrawScope.drawPowerWarGame(state: PowerWarState) {
    val width = size.width
    val height = size.height
    val centerY = height * 0.45f

    // Draw arena
    drawArena(centerY)

    // Draw rope
    drawRope(state.ropePosition, centerY, state)

    // Draw players
    drawWarrior(
        x = width * 0.15f,
        y = centerY,
        color = Color(0xFF4CAF50),
        isPulling = state.playerPower > 100,
        isAnchored = state.playerAnchored,
        facingRight = true
    )

    drawWarrior(
        x = width * 0.85f,
        y = centerY,
        color = Color(0xFFF44336),
        isPulling = state.aiPower > 100,
        isAnchored = state.aiAnchored,
        facingRight = false
    )

    // Draw win zones
    drawWinZone(0f, centerY, Color(0xFF4CAF50).copy(alpha = 0.2f), true)
    drawWinZone(width, centerY, Color(0xFFF44336).copy(alpha = 0.2f), false)
}

private fun DrawScope.drawArena(centerY: Float) {
    val width = size.width
    val height = size.height

    // Ground
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF3D3D3D),
                Color(0xFF2D2D2D)
            ),
            startY = centerY + 100,
            endY = height
        ),
        topLeft = Offset(0f, centerY + 100),
        size = Size(width, height - centerY - 100)
    )

    // Arena circle
    drawCircle(
        color = Color(0xFF4A4A4A),
        radius = 300f,
        center = Offset(width / 2, centerY + 50)
    )
    drawCircle(
        color = Color(0xFF3A3A3A),
        radius = 280f,
        center = Offset(width / 2, centerY + 50)
    )

    // Center line
    drawLine(
        color = Color.White.copy(alpha = 0.5f),
        start = Offset(width / 2, centerY - 150),
        end = Offset(width / 2, centerY + 200),
        strokeWidth = 4f
    )
}

private fun DrawScope.drawRope(position: Float, centerY: Float, state: PowerWarState) {
    val width = size.width
    val startX = width * 0.2f
    val endX = width * 0.8f
    val ropeLength = endX - startX

    // Rope path with sag
    val markerX = startX + ropeLength * position
    val tension = (abs(state.playerPower - state.aiPower) / 200f).coerceIn(0f, 1f)
    val sag = 30f * (1f - tension)

    // Draw rope segments
    val segments = 20
    val path = Path()
    path.moveTo(startX, centerY)

    for (i in 1..segments) {
        val t = i.toFloat() / segments
        val x = startX + ropeLength * t
        val sagAmount = sin(t * Math.PI).toFloat() * sag
        path.lineTo(x, centerY + sagAmount)
    }

    drawPath(
        path = path,
        color = Color(0xFF8B4513),
        style = Stroke(width = 12f, cap = StrokeCap.Round)
    )

    // Draw marker (flag/ribbon)
    val markerY = centerY + sin(position * Math.PI).toFloat() * sag

    // Marker pole
    drawLine(
        color = Color.White,
        start = Offset(markerX, markerY - 60),
        end = Offset(markerX, markerY + 10),
        strokeWidth = 4f
    )

    // Marker flag
    val flagPath = Path().apply {
        moveTo(markerX, markerY - 60)
        lineTo(markerX + 40, markerY - 45)
        lineTo(markerX, markerY - 30)
        close()
    }

    val flagColor = when {
        position > 0.6f -> Color(0xFF4CAF50)  // Player winning
        position < 0.4f -> Color(0xFFF44336)  // AI winning
        else -> Color(0xFFFFEB3B)             // Neutral
    }
    drawPath(flagPath, flagColor)

    // Tension indicator particles
    if (tension > 0.5f) {
        for (i in 0..5) {
            val particleX = markerX + (kotlin.random.Random.nextFloat() - 0.5f) * 40
            val particleY = markerY + (kotlin.random.Random.nextFloat() - 0.5f) * 20
            drawCircle(
                color = Color.Yellow.copy(alpha = 0.5f),
                radius = 3f,
                center = Offset(particleX, particleY)
            )
        }
    }
}

private fun DrawScope.drawWarrior(
    x: Float,
    y: Float,
    color: Color,
    isPulling: Boolean,
    isAnchored: Boolean,
    facingRight: Boolean
) {
    val direction = if (facingRight) 1f else -1f

    // Body
    drawCircle(
        color = color,
        radius = 35f,
        center = Offset(x, y - 60)
    )

    // Torso
    val torsoPullOffset = if (isPulling) direction * -15f else 0f
    drawRoundRect(
        color = color.copy(alpha = 0.9f),
        topLeft = Offset(x - 25 + torsoPullOffset, y - 25),
        size = Size(50f, 80f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f)
    )

    // Arms (pulling pose)
    val armExtension = if (isPulling) 40f else 20f
    drawLine(
        color = color.copy(alpha = 0.8f),
        start = Offset(x + torsoPullOffset, y - 10),
        end = Offset(x + direction * armExtension + torsoPullOffset, y - 30),
        strokeWidth = 15f,
        cap = StrokeCap.Round
    )

    // Legs in stance
    val stanceWidth = if (isAnchored) 40f else 25f
    drawLine(
        color = color.copy(alpha = 0.8f),
        start = Offset(x - stanceWidth / 2, y + 55),
        end = Offset(x - stanceWidth, y + 120),
        strokeWidth = 18f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color.copy(alpha = 0.8f),
        start = Offset(x + stanceWidth / 2, y + 55),
        end = Offset(x + stanceWidth, y + 120),
        strokeWidth = 18f,
        cap = StrokeCap.Round
    )

    // Anchor effect
    if (isAnchored) {
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = 0.3f),
            radius = 80f,
            center = Offset(x, y + 50)
        )
        // Anchor symbol
        val anchorPath = Path().apply {
            moveTo(x, y + 100)
            lineTo(x, y + 140)
            moveTo(x - 20, y + 130)
            quadraticBezierTo(x - 30f, y + 150f, x, y + 140f)
            quadraticBezierTo(x + 30f, y + 150f, x + 20f, y + 130f)
        }
        drawPath(anchorPath, Color(0xFFFFD700), style = Stroke(width = 4f))
    }

    // Effort lines when pulling hard
    if (isPulling) {
        for (i in 0..2) {
            val lineX = x + direction * (50 + i * 15)
            drawLine(
                color = Color.White.copy(alpha = 0.5f - i * 0.15f),
                start = Offset(lineX, y - 40 + i * 10),
                end = Offset(lineX + direction * 20, y - 50 + i * 10),
                strokeWidth = 3f
            )
        }
    }
}

private fun DrawScope.drawWinZone(x: Float, centerY: Float, color: Color, isLeft: Boolean) {
    val zoneWidth = size.width * 0.1f
    val zoneX = if (isLeft) 0f else size.width - zoneWidth

    drawRect(
        color = color,
        topLeft = Offset(zoneX, centerY - 150),
        size = Size(zoneWidth, 350f)
    )

    // "WIN" text zone indicator
    drawRoundRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(zoneX, centerY - 50),
        size = Size(zoneWidth, 100f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
    )
}

@Composable
private fun RoundIndicator(
    round: Int,
    playerWins: Int,
    aiWins: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Player score
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "YOU",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
                Row {
                    repeat(2) { index ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                                .background(
                                    if (index < playerWins) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }

            // Round number
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ROUND",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = round.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // AI score
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF44336)
                )
                Row {
                    repeat(2) { index ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                                .background(
                                    if (index < aiWins) Color(0xFFF44336) else Color.Gray.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerMeter(
    label: String,
    power: Int,
    fatigue: Float,
    isAnchored: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Power value
            Text(
                text = "${power}W",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Power bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Color.Gray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (power / 300f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color, shape = MaterialTheme.shapes.small)
                )
            }

            // Fatigue bar
            if (fatigue > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fatigue",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Yellow.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(Color.Gray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = fatigue)
                                .fillMaxHeight()
                                .background(Color.Yellow, shape = MaterialTheme.shapes.small)
                        )
                    }
                }
            }

            // Anchor status
            if (isAnchored) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ANCHORED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
            }
        }
    }
}

@Composable
private fun AnchorIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "anchor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anchor_alpha"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFD700).copy(alpha = alpha * 0.8f)
        )
    ) {
        Text(
            text = "ANCHOR MODE\nBlocking 50%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(8.dp)
        )
    }
}
