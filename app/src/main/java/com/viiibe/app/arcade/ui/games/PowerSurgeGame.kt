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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PowerSurgeGame(
    state: PowerSurgeState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    // Pulsing animation for charge meter
    val infiniteTransition = rememberInfiniteTransition(label = "charge")
    val chargePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "charge_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF0D1B2A),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        // Game canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPowerSurgeGame(state, chargePulse)
        }

        // Charge meter (bottom center)
        ChargeMeter(
            chargeLevel = state.chargeLevel,
            blastReady = state.blastReady,
            chargePulse = chargePulse,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )

        // Score and combo (top right)
        ScorePanel(
            score = state.score,
            combo = state.combo,
            targetsDestroyed = state.targetsDestroyed,
            totalTargets = state.totalTargets,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp)
        )

        // Blast ready indicator
        if (state.blastReady) {
            BlastReadyIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            )
        }

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Power Surge",
                    instructions = "Pedal to charge energy! Resistance UP to fire blasts at targets!",
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

private fun DrawScope.drawPowerSurgeGame(state: PowerSurgeState, chargePulse: Float) {
    val width = size.width
    val height = size.height

    // Draw energy field background
    drawEnergyField(state.chargeLevel)

    // Draw targets
    state.targets.filter { !it.destroyed }.forEach { target ->
        drawTarget(target, state.chargeLevel)
    }

    // Draw player's energy core
    drawEnergyCore(state.chargeLevel, chargePulse)

    // Draw blast effect if just fired
    if (state.lastBlastTimeMs > 0 &&
        state.gameState.elapsedTimeMs - state.lastBlastTimeMs < 300) {
        drawBlastEffect(state.chargeLevel)
    }

    // Draw destroyed target explosions
    state.targets.filter { it.destroyed }.take(3).forEach { target ->
        drawExplosion(target.x * width, target.y * height)
    }
}

private fun DrawScope.drawEnergyField(chargeLevel: Float) {
    val width = size.width
    val height = size.height

    // Grid lines (energy field effect)
    val gridColor = Color(0xFF1E88E5).copy(alpha = 0.1f + chargeLevel * 0.1f)
    val gridSpacing = 50f

    // Vertical lines
    for (x in 0..width.toInt() step gridSpacing.toInt()) {
        drawLine(
            gridColor,
            Offset(x.toFloat(), 0f),
            Offset(x.toFloat(), height),
            strokeWidth = 1f
        )
    }

    // Horizontal lines
    for (y in 0..height.toInt() step gridSpacing.toInt()) {
        drawLine(
            gridColor,
            Offset(0f, y.toFloat()),
            Offset(width, y.toFloat()),
            strokeWidth = 1f
        )
    }

    // Energy particles floating
    val particleCount = (20 + chargeLevel * 30).toInt()
    for (i in 0 until particleCount) {
        val time = System.currentTimeMillis() / 1000f
        val x = ((i * 73 + time * 20) % width.toInt()).toFloat()
        val y = ((i * 47 + time * 15) % height.toInt()).toFloat()
        val particleAlpha = 0.3f + (sin(time + i.toFloat()) * 0.2f)

        drawCircle(
            color = Color(0xFF64B5F6).copy(alpha = particleAlpha),
            radius = 3f + chargeLevel * 2,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawTarget(target: PowerTarget, chargeLevel: Float) {
    val canvasWidth = this.size.width
    val canvasHeight = this.size.height

    val x = target.x * canvasWidth
    val y = target.y * canvasHeight
    val targetSize = target.size
    val baseRadius = 30f * targetSize.sizeMultiplier

    // Pulsing effect
    val pulse = 1f + kotlin.math.sin(System.currentTimeMillis() / 200f + target.id) * 0.1f

    // Target color based on health
    val healthRatio = target.health.toFloat() / target.maxHealth
    val targetColor = when {
        healthRatio > 0.66f -> Color(0xFFE53935)  // Red - full health
        healthRatio > 0.33f -> Color(0xFFFF9800)  // Orange - damaged
        else -> Color(0xFFFFEB3B)                 // Yellow - critical
    }

    // Outer glow
    drawCircle(
        color = targetColor.copy(alpha = 0.3f),
        radius = baseRadius * pulse * 1.5f,
        center = Offset(x, y)
    )

    // Main body
    drawCircle(
        color = targetColor,
        radius = baseRadius * pulse,
        center = Offset(x, y)
    )

    // Inner core
    drawCircle(
        color = targetColor.copy(alpha = 0.5f),
        radius = baseRadius * pulse * 0.5f,
        center = Offset(x, y)
    )

    // Health indicator ring
    val healthAngle = healthRatio * 360f
    val arcSize = Size(baseRadius * 2.4f, baseRadius * 2.4f)
    drawArc(
        color = Color.White,
        startAngle = -90f,
        sweepAngle = healthAngle,
        useCenter = false,
        topLeft = Offset(x - baseRadius * 1.2f, y - baseRadius * 1.2f),
        size = arcSize,
        style = Stroke(width = 3f)
    )

    // Target symbol (crosshair for bosses)
    if (targetSize == TargetSize.BOSS) {
        drawLine(
            Color.White.copy(alpha = 0.7f),
            Offset(x - baseRadius * 0.7f, y),
            Offset(x + baseRadius * 0.7f, y),
            strokeWidth = 2f
        )
        drawLine(
            Color.White.copy(alpha = 0.7f),
            Offset(x, y - baseRadius * 0.7f),
            Offset(x, y + baseRadius * 0.7f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawEnergyCore(chargeLevel: Float, pulse: Float) {
    val centerX = size.width * 0.15f
    val centerY = size.height * 0.5f
    val baseRadius = 60f

    // Energy rings
    val ringCount = 3
    for (i in 0 until ringCount) {
        val ringRadius = baseRadius + i * 25f + chargeLevel * 20f
        val ringAlpha = (0.5f - i * 0.15f) * chargeLevel
        val rotation = System.currentTimeMillis() / (500f + i * 200) * 360f % 360

        drawArc(
            color = Color(0xFF42A5F5).copy(alpha = ringAlpha),
            startAngle = rotation,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
            size = Size(ringRadius * 2, ringRadius * 2),
            style = Stroke(width = 4f)
        )
        drawArc(
            color = Color(0xFF42A5F5).copy(alpha = ringAlpha),
            startAngle = rotation + 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
            size = Size(ringRadius * 2, ringRadius * 2),
            style = Stroke(width = 4f)
        )
    }

    // Core glow
    val coreRadius = baseRadius * pulse * (0.5f + chargeLevel * 0.5f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF64B5F6),
                Color(0xFF1E88E5).copy(alpha = 0.5f),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = coreRadius * 2
        ),
        radius = coreRadius * 2,
        center = Offset(centerX, centerY)
    )

    // Core
    drawCircle(
        color = Color(0xFF90CAF9),
        radius = coreRadius,
        center = Offset(centerX, centerY)
    )

    // Inner bright spot
    drawCircle(
        color = Color.White,
        radius = coreRadius * 0.3f,
        center = Offset(centerX - coreRadius * 0.2f, centerY - coreRadius * 0.2f)
    )

    // Energy tendrils when highly charged
    if (chargeLevel > 0.5f) {
        val tendrilCount = (chargeLevel * 6).toInt()
        for (i in 0 until tendrilCount) {
            val angle = (System.currentTimeMillis() / 100f + i * 60) % 360 * Math.PI / 180
            val length = baseRadius * 1.5f * chargeLevel
            val endX = centerX + cos(angle).toFloat() * length
            val endY = centerY + sin(angle).toFloat() * length

            drawLine(
                color = Color(0xFF64B5F6).copy(alpha = 0.6f),
                start = Offset(centerX, centerY),
                end = Offset(endX, endY),
                strokeWidth = 3f
            )
        }
    }
}

private fun DrawScope.drawBlastEffect(chargeLevel: Float) {
    val startX = size.width * 0.15f
    val startY = size.height * 0.5f

    // Blast beam
    val beamPath = Path().apply {
        moveTo(startX + 60f, startY - 20f)
        lineTo(size.width, startY - 10f - Random.nextFloat() * 20)
        lineTo(size.width, startY + 10f + Random.nextFloat() * 20)
        lineTo(startX + 60f, startY + 20f)
        close()
    }

    drawPath(
        beamPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF64B5F6),
                Color(0xFF2196F3).copy(alpha = 0.7f),
                Color(0xFF1565C0).copy(alpha = 0.3f)
            )
        )
    )

    // Blast particles
    for (i in 0..20) {
        val t = i / 20f
        val x = startX + (size.width - startX) * t
        val y = startY + (Random.nextFloat() - 0.5f) * 40
        drawCircle(
            color = Color.White.copy(alpha = 1f - t),
            radius = 5f * (1f - t),
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawExplosion(x: Float, y: Float) {
    val time = System.currentTimeMillis() % 500
    val progress = time / 500f
    val radius = 50f * progress

    // Explosion rings
    for (i in 0..2) {
        val ringRadius = radius * (1f - i * 0.2f)
        val alpha = (1f - progress) * (1f - i * 0.3f)
        drawCircle(
            color = Color(0xFFFF9800).copy(alpha = alpha),
            radius = ringRadius,
            center = Offset(x, y),
            style = Stroke(width = 5f - i * 1.5f)
        )
    }

    // Particles
    for (i in 0..8) {
        val angle = i * 45f * Math.PI / 180
        val particleX = x + cos(angle).toFloat() * radius * 1.2f
        val particleY = y + sin(angle).toFloat() * radius * 1.2f
        drawCircle(
            color = Color(0xFFFFEB3B).copy(alpha = 1f - progress),
            radius = 4f,
            center = Offset(particleX, particleY)
        )
    }
}

@Composable
private fun ChargeMeter(
    chargeLevel: Float,
    blastReady: Boolean,
    chargePulse: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (blastReady) "BLAST READY!" else "CHARGING...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (blastReady) Color(0xFF4CAF50) else Color(0xFF64B5F6)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Charge bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color.Gray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(chargeLevel * if (blastReady) chargePulse else 1f)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (blastReady) {
                                    listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
                                } else {
                                    listOf(Color(0xFF1E88E5), Color(0xFF64B5F6))
                                }
                            ),
                            shape = MaterialTheme.shapes.small
                        )
                )

                // 50% marker (blast threshold)
                Box(
                    modifier = Modifier
                        .offset(x = 150.dp - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(chargeLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            if (blastReady) {
                Text(
                    text = "Resistance UP to FIRE!",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun ScorePanel(
    score: Int,
    combo: Int,
    targetsDestroyed: Int,
    totalTargets: Int,
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
            Text("SCORE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64B5F6)
            )

            if (combo > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text(
                        text = "${combo}x COMBO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("TARGETS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Text(
                text = "$targetsDestroyed / $totalTargets",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun BlastReadyIndicator(modifier: Modifier = Modifier) {
    val pulseTransition = rememberInfiniteTransition(label = "blast_pulse")
    val scale by pulseTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(200), RepeatMode.Reverse),
        label = "blast_scale"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.9f))
    ) {
        Text(
            text = "FIRE!",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize * scale
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
