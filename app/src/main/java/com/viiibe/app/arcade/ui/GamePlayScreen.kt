package com.viiibe.app.arcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.viiibe.app.arcade.ArcadeViewModel
import com.viiibe.app.arcade.data.*
import com.viiibe.app.arcade.ui.games.*
import com.viiibe.app.bluetooth.BluetoothViewModel

@Composable
fun GamePlayScreen(
    game: ArcadeGame,
    difficulty: AIDifficulty,
    bluetoothViewModel: BluetoothViewModel,
    userId: Long = 0,
    walletAddress: String? = null,
    statsSyncAllowed: Boolean = true,
    onExitGame: () -> Unit
) {
    val arcadeViewModel: ArcadeViewModel = viewModel()
    val metrics by bluetoothViewModel.metrics.collectAsState()

    // Game states
    val sprintRaceState by arcadeViewModel.sprintRaceState.collectAsState()
    val powerWarState by arcadeViewModel.powerWarState.collectAsState()
    val rhythmRideState by arcadeViewModel.rhythmRideState.collectAsState()
    val zombieEscapeState by arcadeViewModel.zombieEscapeState.collectAsState()
    val hillClimbState by arcadeViewModel.hillClimbState.collectAsState()
    val powerSurgeState by arcadeViewModel.powerSurgeState.collectAsState()
    val musicSpeedState by arcadeViewModel.musicSpeedState.collectAsState()
    val musicSpeedProgress by arcadeViewModel.musicSpeedProgress.collectAsState()
    val paperRouteState by arcadeViewModel.paperRouteState.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showResultsDialog by remember { mutableStateOf(false) }
    var gameResult by remember { mutableStateOf<ArcadeGameResult?>(null) }

    // Set the user ID, wallet address, and stats sync permission for saving and syncing game results
    LaunchedEffect(userId, walletAddress, statsSyncAllowed) {
        if (userId > 0) {
            arcadeViewModel.setUserId(userId, walletAddress, statsSyncAllowed)
        }
    }

    // Initialize game on first composition
    LaunchedEffect(game, difficulty) {
        arcadeViewModel.initializeGame(game, difficulty)
    }

    // Feed bike metrics to the game engine
    // Peloton reports watts * 10, divide to get actual watts
    LaunchedEffect(metrics) {
        arcadeViewModel.updatePlayerMetrics(
            PlayerMetrics(
                cadence = metrics.cadence,
                power = metrics.power / 10,
                resistance = metrics.resistance
            )
        )
    }

    // Check for game completion
    val currentPhase = when (game) {
        ArcadeGame.SPRINT_RACE -> sprintRaceState.gameState.phase
        ArcadeGame.POWER_WAR -> powerWarState.gameState.phase
        ArcadeGame.RHYTHM_RIDE -> rhythmRideState.gameState.phase
        ArcadeGame.ZOMBIE_ESCAPE -> zombieEscapeState.gameState.phase
        ArcadeGame.HILL_CLIMB -> hillClimbState.gameState.phase
        ArcadeGame.POWER_SURGE -> powerSurgeState.gameState.phase
        ArcadeGame.MUSIC_SPEED -> musicSpeedState.gameState.phase
        ArcadeGame.PAPER_ROUTE -> paperRouteState.gameState.phase
    }

    LaunchedEffect(currentPhase) {
        if (currentPhase == GamePhase.FINISHED) {
            // Save the game result to local database and get result for display
            gameResult = arcadeViewModel.saveAndGetGameResult()
            showResultsDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Game content
        when (game) {
            ArcadeGame.SPRINT_RACE -> SprintRaceGame(
                state = sprintRaceState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.POWER_WAR -> PowerWarGame(
                state = powerWarState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.RHYTHM_RIDE -> RhythmRideGame(
                state = rhythmRideState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.ZOMBIE_ESCAPE -> ZombieEscapeGame(
                state = zombieEscapeState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.HILL_CLIMB -> HillClimbGame(
                state = hillClimbState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.POWER_SURGE -> PowerSurgeGame(
                state = powerSurgeState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.MUSIC_SPEED -> MusicSpeedGame(
                state = musicSpeedState,
                progress = musicSpeedProgress,
                playerMetrics = metrics,
                onSelectArtist = { artist -> arcadeViewModel.selectMusicArtist(artist) },
                onSelectSong = { song -> arcadeViewModel.selectMusicSong(song) },
                onUnlockArtist = { artist -> arcadeViewModel.unlockMusicArtist(artist) },
                onStartGame = { arcadeViewModel.startGame() }
            )
            ArcadeGame.PAPER_ROUTE -> PaperRouteGame(
                state = paperRouteState,
                playerMetrics = metrics,
                onStartGame = { arcadeViewModel.startGame() }
            )
        }

        // Top bar overlay
        GameTopBar(
            game = game,
            gameState = when (game) {
                ArcadeGame.SPRINT_RACE -> sprintRaceState.gameState
                ArcadeGame.POWER_WAR -> powerWarState.gameState
                ArcadeGame.RHYTHM_RIDE -> rhythmRideState.gameState
                ArcadeGame.ZOMBIE_ESCAPE -> zombieEscapeState.gameState
                ArcadeGame.HILL_CLIMB -> hillClimbState.gameState
                ArcadeGame.POWER_SURGE -> powerSurgeState.gameState
                ArcadeGame.MUSIC_SPEED -> musicSpeedState.gameState
                ArcadeGame.PAPER_ROUTE -> paperRouteState.gameState
            },
            onPause = { arcadeViewModel.pauseGame() },
            onResume = { arcadeViewModel.resumeGame() },
            onExit = { showExitDialog = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Metrics display overlay
        // Power is already converted to actual watts in PelotonSensorService
        MetricsOverlay(
            cadence = metrics.cadence,
            power = metrics.power,
            resistance = metrics.resistance,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Game?") },
            text = { Text("Your progress will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        arcadeViewModel.endGame()
                        onExitGame()
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue Playing")
                }
            }
        )
    }

    // Results dialog
    if (showResultsDialog && gameResult != null) {
        GameResultsDialog(
            result = gameResult!!,
            statsSyncBlocked = arcadeViewModel.isStatsSyncBlocked(),
            onPlayAgain = {
                showResultsDialog = false
                arcadeViewModel.initializeGame(game, difficulty)
            },
            onExit = {
                showResultsDialog = false
                onExitGame()
            }
        )
    }
}

@Composable
private fun GameTopBar(
    game: ArcadeGame,
    gameState: GameState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Exit button
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Exit",
                tint = Color.White
            )
        }

        // Game name and timer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = game.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (gameState.phase == GamePhase.PLAYING || gameState.phase == GamePhase.PAUSED) {
                val remainingMs = gameState.gameDurationMs - gameState.elapsedTimeMs
                val minutes = (remainingMs / 60000).toInt()
                val seconds = ((remainingMs % 60000) / 1000).toInt()
                Text(
                    text = String.format("%d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingMs < 10000) Color.Red else Color.White
                )
            }
        }

        // Pause/Resume button
        if (gameState.phase == GamePhase.PLAYING) {
            IconButton(onClick = onPause) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Pause",
                    tint = Color.White
                )
            }
        } else if (gameState.phase == GamePhase.PAUSED) {
            IconButton(onClick = onResume) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun MetricsOverlay(
    cadence: Int,
    power: Int,
    resistance: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricItem(label = "RPM", value = cadence.toString())
            MetricItem(label = "WATTS", value = power.toString())
            MetricItem(label = "RES", value = resistance.toString())
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun GameResultsDialog(
    result: ArcadeGameResult,
    statsSyncBlocked: Boolean = false,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (result.won) Icons.Filled.EmojiEvents else Icons.Filled.SportsScore,
                    contentDescription = null,
                    tint = if (result.won) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (result.won) "Victory!" else "Game Over",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = result.game.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Divider()

                ResultRow("Score", result.score.toString())
                ResultRow("Duration", "${result.durationSeconds}s")
                ResultRow("Difficulty", result.difficulty.displayName)

                // Game-specific details
                result.details.forEach { (key, value) ->
                    ResultRow(key, value.toString())
                }

                // Stats sync blocked notice
                if (statsSyncBlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Update app to save stats online",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onPlayAgain) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play Again")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onExit) {
                Text("Exit")
            }
        }
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold
        )
    }
}
