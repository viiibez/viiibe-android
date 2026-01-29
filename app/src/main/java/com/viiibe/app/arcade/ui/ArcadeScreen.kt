package com.viiibe.app.arcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viiibe.app.arcade.data.AIDifficulty
import com.viiibe.app.arcade.data.ArcadeGame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcadeScreen(
    onSelectGame: (ArcadeGame, AIDifficulty) -> Unit
) {
    var selectedGame by remember { mutableStateOf<ArcadeGame?>(null) }
    var showDifficultyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Text(
            text = "Bike Arcade",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Control games with your bike - no touch needed during gameplay!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Input legend
        InputLegendCard()

        Spacer(modifier = Modifier.height(24.dp))

        // Game selection grid
        Text(
            text = "Select a Game",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ArcadeGame.values().toList()) { game ->
                GameCard(
                    game = game,
                    onClick = {
                        selectedGame = game
                        showDifficultyDialog = true
                    }
                )
            }
        }
    }

    // Difficulty selection dialog
    if (showDifficultyDialog && selectedGame != null) {
        DifficultyDialog(
            game = selectedGame!!,
            onDismiss = { showDifficultyDialog = false },
            onSelectDifficulty = { difficulty ->
                showDifficultyDialog = false
                onSelectGame(selectedGame!!, difficulty)
            }
        )
    }
}

@Composable
private fun InputLegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InputLegendItem(
                icon = Icons.Filled.Speed,
                label = "Cadence",
                action = "Speed / Movement"
            )
            InputLegendItem(
                icon = Icons.Filled.Bolt,
                label = "Power Burst",
                action = "Boost / Special"
            )
            InputLegendItem(
                icon = Icons.Filled.ArrowUpward,
                label = "Resistance +10",
                action = "Jump / Action 1"
            )
            InputLegendItem(
                icon = Icons.Filled.ArrowDownward,
                label = "Resistance -10",
                action = "Slide / Action 2"
            )
        }
    }
}

@Composable
private fun InputLegendItem(
    icon: ImageVector,
    label: String,
    action: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = action,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GameCard(
    game: ArcadeGame,
    onClick: () -> Unit
) {
    val (icon, gradientColors) = when (game) {
        ArcadeGame.SPRINT_RACE -> Icons.Filled.DirectionsRun to listOf(
            Color(0xFF4CAF50),
            Color(0xFF2E7D32)
        )
        ArcadeGame.POWER_WAR -> Icons.Filled.FitnessCenter to listOf(
            Color(0xFFFF5722),
            Color(0xFFBF360C)
        )
        ArcadeGame.RHYTHM_RIDE -> Icons.Filled.MusicNote to listOf(
            Color(0xFF9C27B0),
            Color(0xFF6A1B9A)
        )
        ArcadeGame.ZOMBIE_ESCAPE -> Icons.Filled.Warning to listOf(
            Color(0xFF424242),
            Color(0xFF1B5E20)
        )
        ArcadeGame.HILL_CLIMB -> Icons.Filled.Terrain to listOf(
            Color(0xFF795548),
            Color(0xFF4E342E)
        )
        ArcadeGame.POWER_SURGE -> Icons.Filled.Bolt to listOf(
            Color(0xFFFFEB3B),
            Color(0xFFF57F17)
        )
        ArcadeGame.MUSIC_SPEED -> Icons.Filled.Speed to listOf(
            Color(0xFFE91E63),
            Color(0xFFC2185B)
        )
        ArcadeGame.PAPER_ROUTE -> Icons.Filled.Newspaper to listOf(
            Color(0xFF03A9F4),
            Color(0xFF01579B)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(gradientColors)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = game.displayName,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )

                Column {
                    Text(
                        text = game.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = game.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Players",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (game.minPlayers == game.maxPlayers) {
                            "${game.minPlayers} player"
                        } else {
                            "${game.minPlayers}-${game.maxPlayers} players"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyDialog(
    game: ArcadeGame,
    onDismiss: () -> Unit,
    onSelectDifficulty: (AIDifficulty) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Difficulty",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                AIDifficulty.values().forEach { difficulty ->
                    DifficultyOption(
                        difficulty = difficulty,
                        onClick = { onSelectDifficulty(difficulty) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DifficultyOption(
    difficulty: AIDifficulty,
    onClick: () -> Unit
) {
    val color = when (difficulty) {
        AIDifficulty.EASY -> Color(0xFF4CAF50)
        AIDifficulty.MEDIUM -> Color(0xFFFFC107)
        AIDifficulty.HARD -> Color(0xFFFF9800)
        AIDifficulty.EXTREME -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = difficulty.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                Text(
                    text = "AI Cadence: ${difficulty.cadenceRange.first}-${difficulty.cadenceRange.last} RPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = color
            )
        }
    }
}
