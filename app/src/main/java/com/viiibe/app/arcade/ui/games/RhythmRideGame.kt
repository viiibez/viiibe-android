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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RhythmRideGame(
    state: RhythmRideState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    // Pulsing animation for beat
    val infiniteTransition = rememberInfiniteTransition(label = "beat")
    val beatPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000 / state.bpm / 2),  // Half beat duration
            repeatMode = RepeatMode.Reverse
        ),
        label = "beat_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0A2E),
                        Color(0xFF2D1B4E),
                        Color(0xFF1A0A2E)
                    )
                )
            )
    ) {
        // Game canvas - scrolling targets
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRhythmGame(state, beatPulse)
        }

        // Current cadence display (large, centered)
        CurrentCadenceDisplay(
            currentCadence = playerMetrics.cadence,
            targetCadence = state.targetCadences.getOrNull(state.currentTargetIndex)?.targetCadence,
            beatPulse = beatPulse,
            modifier = Modifier.align(Alignment.Center)
        )

        // Score and combo display
        ScoreComboDisplay(
            score = state.score,
            combo = state.combo,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp)
        )

        // Hit feedback - use lastHitResult for cleaner timing
        state.lastHitResult?.let { result ->
            HitFeedback(
                result = result,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-100).dp)
            )
        }

        // Power multiplier indicator
        if (state.powerMultiplier > 1.0f) {
            PowerMultiplierBadge(
                multiplier = state.powerMultiplier,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
        }

        // Stats panel
        StatsPanel(
            perfect = state.perfectHits,
            good = state.goodHits,
            miss = state.misses,
            maxCombo = state.maxCombo,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // BPM indicator
        BpmIndicator(
            bpm = state.bpm,
            beatPulse = beatPulse,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 60.dp, start = 16.dp)
        )

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Rhythm Ride",
                    instructions = "Match the target cadence when notes arrive!\n" +
                        "PERFECT: within tolerance, GOOD: close\n" +
                        "Pedal harder (more watts) for bonus points!",
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

private fun DrawScope.drawRhythmGame(state: RhythmRideState, beatPulse: Float) {
    val width = size.width
    val height = size.height
    val centerX = width / 2
    val targetZoneY = height * 0.5f

    // Draw background effects
    drawBackgroundEffects(state, beatPulse)

    // Draw track lanes
    drawTrackLanes(centerX, targetZoneY)

    // Draw target zone (hit line)
    drawTargetZone(centerX, targetZoneY, beatPulse)

    // Draw scrolling cadence targets
    drawCadenceTargets(state, centerX, targetZoneY)
}

private fun DrawScope.drawBackgroundEffects(state: RhythmRideState, beatPulse: Float) {
    val width = size.width
    val height = size.height

    // Pulsing rings based on combo
    if (state.combo > 0) {
        val ringCount = minOf(state.combo / 5, 5)
        for (i in 0 until ringCount) {
            val radius = 100f + i * 80f + (beatPulse - 1f) * 50f
            drawCircle(
                color = Color(0xFF9C27B0).copy(alpha = 0.1f - i * 0.02f),
                radius = radius,
                center = Offset(width / 2, height / 2)
            )
        }
    }

    // Floating music notes (decorative)
    val notePositions = listOf(
        Offset(width * 0.1f, height * 0.3f),
        Offset(width * 0.9f, height * 0.4f),
        Offset(width * 0.15f, height * 0.7f),
        Offset(width * 0.85f, height * 0.6f)
    )

    notePositions.forEachIndexed { index, pos ->
        val wobble = sin((state.gameState.elapsedTimeMs / 1000.0 + index) * 2).toFloat() * 10f
        drawMusicNote(pos.x, pos.y + wobble, Color.White.copy(alpha = 0.1f))
    }
}

private fun DrawScope.drawMusicNote(x: Float, y: Float, color: Color) {
    // Simple music note shape
    drawCircle(color, radius = 8f, center = Offset(x, y))
    drawLine(color, Offset(x + 8, y), Offset(x + 8, y - 30), strokeWidth = 3f)
    drawLine(color, Offset(x + 8, y - 30), Offset(x + 20, y - 25), strokeWidth = 3f)
}

private fun DrawScope.drawTrackLanes(centerX: Float, targetZoneY: Float) {
    val trackWidth = 200f
    val height = size.height

    // Main track
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF4A148C).copy(alpha = 0.3f),
                Color(0xFF4A148C).copy(alpha = 0.5f),
                Color(0xFF4A148C).copy(alpha = 0.3f),
                Color.Transparent
            ),
            startX = centerX - trackWidth,
            endX = centerX + trackWidth
        ),
        topLeft = Offset(centerX - trackWidth, 0f),
        size = Size(trackWidth * 2, height)
    )

    // Track edges
    drawLine(
        color = Color(0xFF9C27B0).copy(alpha = 0.5f),
        start = Offset(centerX - trackWidth / 2, 0f),
        end = Offset(centerX - trackWidth / 2, height),
        strokeWidth = 2f
    )
    drawLine(
        color = Color(0xFF9C27B0).copy(alpha = 0.5f),
        start = Offset(centerX + trackWidth / 2, 0f),
        end = Offset(centerX + trackWidth / 2, height),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawTargetZone(centerX: Float, y: Float, beatPulse: Float) {
    val zoneWidth = 250f * beatPulse
    val zoneHeight = 60f

    // Glow effect
    drawRoundRect(
        color = Color(0xFF9C27B0).copy(alpha = 0.3f),
        topLeft = Offset(centerX - zoneWidth / 2 - 10, y - zoneHeight / 2 - 10),
        size = Size(zoneWidth + 20, zoneHeight + 20),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f)
    )

    // Main zone
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF7B1FA2),
                Color(0xFF9C27B0),
                Color(0xFF7B1FA2)
            )
        ),
        topLeft = Offset(centerX - zoneWidth / 2, y - zoneHeight / 2),
        size = Size(zoneWidth, zoneHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f)
    )

    // Hit line
    drawLine(
        color = Color.White,
        start = Offset(centerX - zoneWidth / 2 + 10, y),
        end = Offset(centerX + zoneWidth / 2 - 10, y),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // "HIT" text indicator dots
    for (i in -2..2) {
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = 4f,
            center = Offset(centerX + i * 20f, y)
        )
    }
}

private fun DrawScope.drawCadenceTargets(state: RhythmRideState, centerX: Float, targetZoneY: Float) {
    val currentTime = state.gameState.elapsedTimeMs
    val visibleRange = 3000L  // Show targets 3 seconds ahead

    state.targetCadences.forEach { target ->
        val timeUntilHit = target.startTimeMs - currentTime
        val timeAfterHit = currentTime - (target.startTimeMs + target.durationMs)

        // Only draw if in visible range
        if (timeUntilHit < visibleRange && timeAfterHit < 500) {
            val progress = 1f - (timeUntilHit / visibleRange.toFloat())
            val y = targetZoneY * progress * 1.1f  // Approaches from top

            val targetSize = 120f
            val alpha = when {
                timeAfterHit > 0 -> maxOf(0f, 1f - timeAfterHit / 500f)  // Fade out after passing
                else -> minOf(1f, progress + 0.3f)
            }

            // Target color based on hit result
            val color = when (target.hit) {
                HitResult.PENDING -> Color(0xFFE91E63)
                HitResult.PERFECT -> Color(0xFF4CAF50)
                HitResult.GOOD -> Color(0xFFFFEB3B)
                HitResult.MISS -> Color(0xFFF44336).copy(alpha = 0.5f)
            }

            // Draw target
            drawRoundRect(
                color = color.copy(alpha = alpha * 0.8f),
                topLeft = Offset(centerX - targetSize / 2, y - 25),
                size = Size(targetSize, 50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
            )

            // Cadence value text would go here, but Canvas doesn't support text easily
            // We'll show the value via the indicator below

            // Draw target cadence indicator line
            val indicatorWidth = (target.targetCadence / 120f) * targetSize
            drawRoundRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(centerX - indicatorWidth / 2, y - 5),
                size = Size(indicatorWidth, 10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
        }
    }
}

@Composable
private fun CurrentCadenceDisplay(
    currentCadence: Int,
    targetCadence: Int?,
    beatPulse: Float,
    modifier: Modifier = Modifier
) {
    val cadenceDiff = targetCadence?.let { abs(currentCadence - it) } ?: 0
    val matchColor = when {
        targetCadence == null -> Color.White
        cadenceDiff <= 5 -> Color(0xFF4CAF50)  // Perfect
        cadenceDiff <= 10 -> Color(0xFFFFEB3B)  // Good
        else -> Color(0xFFF44336)  // Off
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Current cadence
            Text(
                text = currentCadence.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = (80 * beatPulse).sp
                ),
                fontWeight = FontWeight.Bold,
                color = matchColor
            )

            Text(
                text = "RPM",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            // Target cadence
            if (targetCadence != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TARGET:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = targetCadence.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE91E63)
                    )
                }

                // Difference indicator
                if (cadenceDiff > 0) {
                    val direction = if (currentCadence < targetCadence) "+" else "-"
                    Text(
                        text = "$direction$cadenceDiff RPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = matchColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreComboDisplay(
    score: Int,
    combo: Int,
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
            horizontalAlignment = Alignment.End
        ) {
            // Score
            Text(
                text = "SCORE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9C27B0)
            )

            // Combo
            if (combo > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text(
                        text = "${combo}x COMBO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HitFeedback(
    result: HitResult,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (result) {
        HitResult.PERFECT -> "PERFECT!" to Color(0xFF4CAF50)
        HitResult.GOOD -> "GOOD!" to Color(0xFFFFEB3B)
        HitResult.MISS -> "MISS" to Color(0xFFF44336)
        HitResult.PENDING -> return
    }

    val alpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(500),
        label = "hit_fade"
    )

    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = color.copy(alpha = 1f - alpha),
        modifier = modifier
    )
}

@Composable
private fun StatsPanel(
    perfect: Int,
    good: Int,
    miss: Int,
    maxCombo: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            StatRow("Perfect", perfect, Color(0xFF4CAF50))
            StatRow("Good", good, Color(0xFFFFEB3B))
            StatRow("Miss", miss, Color(0xFFF44336))
            Divider(
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            StatRow("Max Combo", maxCombo, Color(0xFFFF9800))
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, color: Color) {
    Row(
        modifier = Modifier.width(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun BpmIndicator(
    bpm: Int,
    beatPulse: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF9C27B0).copy(alpha = beatPulse - 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Beat dot
            Box(
                modifier = Modifier
                    .size((12 * beatPulse).dp)
                    .background(Color.White, shape = MaterialTheme.shapes.small)
            )

            Text(
                text = "$bpm BPM",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PowerMultiplierBadge(
    multiplier: Float,
    modifier: Modifier = Modifier
) {
    // Color intensity based on multiplier level
    val badgeColor = when {
        multiplier >= 2.0f -> Color(0xFFFF5722)  // Deep orange for max power
        multiplier >= 1.5f -> Color(0xFFFF9800)  // Orange for high power
        else -> Color(0xFFFFC107)                // Amber for moderate power
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = badgeColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Lightning bolt icon (using text as simple placeholder)
            Text(
                text = "POWER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = "%.2fx".format(multiplier),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
