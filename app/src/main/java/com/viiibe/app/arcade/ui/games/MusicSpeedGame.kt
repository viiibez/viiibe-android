package com.viiibe.app.arcade.ui.games

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viiibe.app.arcade.data.*
import com.viiibe.app.data.model.RideMetrics
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MusicSpeedGame(
    state: MusicSpeedState,
    progress: MusicSpeedProgress,
    playerMetrics: RideMetrics,
    onSelectArtist: (MusicArtist) -> Unit,
    onSelectSong: (Song) -> Unit,
    onStartGame: () -> Unit,
    onUnlockArtist: (MusicArtist) -> Unit
) {
    if (state.inSongSelect) {
        SongSelectionScreen(
            state = state,
            progress = progress,
            onSelectArtist = onSelectArtist,
            onSelectSong = onSelectSong,
            onUnlockArtist = onUnlockArtist
        )
    } else {
        MusicPlayScreen(
            state = state,
            playerMetrics = playerMetrics,
            onStartGame = onStartGame
        )
    }
}

@Composable
private fun SongSelectionScreen(
    state: MusicSpeedState,
    progress: MusicSpeedProgress,
    onSelectArtist: (MusicArtist) -> Unit,
    onSelectSong: (Song) -> Unit,
    onUnlockArtist: (MusicArtist) -> Unit
) {
    Column(
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
            .padding(16.dp)
    ) {
        // Header with points
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Music Speed",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Points display
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${progress.totalPoints}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose an artist and song",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Artists horizontal scroll
        Text(
            text = "Artists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(MusicLibrary.artists) { artist ->
                val isUnlocked = artist.id in progress.unlockedArtistIds
                val isSelected = state.selectedArtist?.id == artist.id

                ArtistCard(
                    artist = artist,
                    isUnlocked = isUnlocked,
                    isSelected = isSelected,
                    currentPoints = progress.totalPoints,
                    onClick = {
                        if (isUnlocked) {
                            onSelectArtist(artist)
                        }
                    },
                    onUnlock = { onUnlockArtist(artist) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Songs list for selected artist
        if (state.selectedArtist != null) {
            Text(
                text = "Songs by ${state.selectedArtist.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.selectedArtist.songs) { song ->
                    val highScore = progress.highScores[song.id] ?: 0
                    SongCard(
                        song = song,
                        highScore = highScore,
                        isSelected = state.selectedSong?.id == song.id,
                        onClick = { onSelectSong(song) }
                    )
                }
            }
        } else {
            // Placeholder when no artist selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select an artist to see songs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: MusicArtist,
    isUnlocked: Boolean,
    isSelected: Boolean,
    currentPoints: Int,
    onClick: () -> Unit,
    onUnlock: () -> Unit
) {
    val canAfford = currentPoints >= artist.unlockCost

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(enabled = isUnlocked, onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    Color(0xFF00D9FF),
                    RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked)
                Color(0xFF2A2A4A)
            else
                Color(0xFF1A1A2A)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Artist icon/avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked)
                            genreColor(artist.genre)
                        else
                            Color.Gray.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                text = artist.genre,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUnlocked) genreColor(artist.genre) else Color.Gray,
                maxLines = 1
            )

            if (!isUnlocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onUnlock,
                    enabled = canAfford,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canAfford) Color(0xFFFFD700) else Color.Gray,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${artist.unlockCost}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    highScore: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    Color(0xFF00D9FF),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2A3A5A) else Color(0xFF1E1E3A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(difficultyColor(song.difficulty)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSelected) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = song.difficulty.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = difficultyColor(song.difficulty)
                    )
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "${song.baseBpm} BPM",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // High score
            if (highScore > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Best",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "$highScore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicPlayScreen(
    state: MusicSpeedState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    // Animated visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val animatedBars = remember { mutableStateListOf(*Array(20) { 0f }) }

    // Update bars based on cadence
    LaunchedEffect(state.currentCadence, state.playbackSpeed) {
        for (i in animatedBars.indices) {
            val base = (state.currentCadence / 120f).coerceIn(0.1f, 1f)
            val variation = (sin(System.currentTimeMillis() / 100.0 + i * 0.5) * 0.3f).toFloat()
            animatedBars[i] = (base + variation + Random.nextFloat() * 0.2f).coerceIn(0.1f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D1A),
                        Color(0xFF1A0A2E),
                        Color(0xFF2D1B4E)
                    )
                )
            )
    ) {
        // Visualizer canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVisualizer(animatedBars.toList(), state.playbackSpeed)
        }

        // Game content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Song info
            state.selectedSong?.let { song ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = state.selectedArtist?.name ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Speed indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedIndicatorCard(
                    label = "SPEED",
                    value = "%.1fx".format(state.playbackSpeed),
                    color = speedColor(state.playbackSpeed)
                )
                SpeedIndicatorCard(
                    label = "BPM",
                    value = "${state.bpm}",
                    color = Color(0xFF00D9FF)
                )
                SpeedIndicatorCard(
                    label = "CADENCE",
                    value = "${state.currentCadence}",
                    color = Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Score and points
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScoreCard(
                    label = "SCORE",
                    value = state.score,
                    color = Color.White
                )
                ScoreCard(
                    label = "POINTS",
                    value = state.pointsEarned,
                    color = Color(0xFFFFD700),
                    icon = Icons.Filled.Star
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime((state.songProgress * (state.selectedSong?.durationMs ?: 0)).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatTime(state.selectedSong?.durationMs ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.songProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = speedColor(state.playbackSpeed),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speed guide
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedGuideItem("60 RPM", "0.5x", Color(0xFF64B5F6))
                    SpeedGuideItem("80 RPM", "1.0x", Color(0xFF4CAF50))
                    SpeedGuideItem("100 RPM", "1.5x", Color(0xFFFFB74D))
                    SpeedGuideItem("120+ RPM", "2.0x", Color(0xFFE57373))
                }
            }
        }

        // Countdown / Start overlay
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                StartOverlay(
                    gameName = "Music Speed",
                    instructions = "Pedal to control the music speed!\nFaster cadence = faster music = more points!",
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

@Composable
private fun SpeedIndicatorCard(
    label: String,
    value: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ScoreCard(
    label: String,
    value: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun SpeedGuideItem(rpm: String, speed: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = speed,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = rpm,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

private fun DrawScope.drawVisualizer(bars: List<Float>, speed: Float) {
    val barWidth = size.width / (bars.size * 2)
    val maxHeight = size.height * 0.6f
    val centerY = size.height / 2

    bars.forEachIndexed { index, level ->
        val x = barWidth + (index * barWidth * 2)
        val barHeight = level * maxHeight

        // Color based on speed
        val color = when {
            speed >= 1.8f -> Color(0xFFE57373)
            speed >= 1.4f -> Color(0xFFFFB74D)
            speed >= 1.0f -> Color(0xFF4CAF50)
            else -> Color(0xFF64B5F6)
        }

        // Draw bar with glow
        drawRoundRect(
            color = color.copy(alpha = 0.3f),
            topLeft = Offset(x - 4, centerY - barHeight / 2 - 4),
            size = Size(barWidth + 8, barHeight + 8),
            cornerRadius = CornerRadius(8f)
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(x, centerY - barHeight / 2),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(4f)
        )
    }
}

private fun genreColor(genre: String): Color = when (genre.lowercase()) {
    "synthwave" -> Color(0xFFE040FB)
    "electronic" -> Color(0xFF00BCD4)
    "hip hop" -> Color(0xFFFF9800)
    "rock" -> Color(0xFFF44336)
    "latin" -> Color(0xFFFFEB3B)
    else -> Color(0xFF9C27B0)
}

private fun difficultyColor(difficulty: SongDifficulty): Color = when (difficulty) {
    SongDifficulty.EASY -> Color(0xFF4CAF50)
    SongDifficulty.MEDIUM -> Color(0xFFFFB74D)
    SongDifficulty.HARD -> Color(0xFFFF7043)
    SongDifficulty.EXTREME -> Color(0xFFE53935)
}

private fun speedColor(speed: Float): Color = when {
    speed >= 1.8f -> Color(0xFFE57373)
    speed >= 1.4f -> Color(0xFFFFB74D)
    speed >= 1.0f -> Color(0xFF4CAF50)
    else -> Color(0xFF64B5F6)
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}
