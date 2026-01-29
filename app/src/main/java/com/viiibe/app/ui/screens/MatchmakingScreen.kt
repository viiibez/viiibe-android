package com.viiibe.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viiibe.app.arcade.data.ArcadeGame
import com.viiibe.app.arcade.p2p.*
import com.viiibe.app.version.VersionState
import kotlinx.coroutines.delay

/**
 * Global Matchmaking Screen for server-relayed games.
 *
 * This screen provides:
 * - Game type and mode selection
 * - Stake amount input for wagered games
 * - "Find Match" button to join queue
 * - Queue status with position and estimated wait time
 * - Match found popup with accept/decline
 * - Animated searching indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchmakingScreen(
    serverConnectionManager: ServerConnectionManager,
    viiibeBalance: Double,
    walletAddress: String?,
    playerName: String,
    versionState: VersionState = VersionState(),
    currentVersion: String = "",
    onOpenDownloadUrl: () -> Unit = {},
    onStartGame: (ArcadeGame, MatchInfo) -> Unit,
    onBack: () -> Unit
) {
    val connectionState by serverConnectionManager.connectionState.collectAsState()
    val isAuthenticated by serverConnectionManager.isAuthenticated.collectAsState()
    val matchmakingState by serverConnectionManager.matchmakingState.collectAsState()
    val matchInfo by serverConnectionManager.matchInfo.collectAsState()
    val queuePosition by serverConnectionManager.queuePosition.collectAsState()
    val estimatedWaitMs by serverConnectionManager.estimatedWaitMs.collectAsState()

    var selectedGame by remember { mutableStateOf<ArcadeGame?>(null) }
    var stakeAmount by remember { mutableStateOf("10") }
    var isFriendlyMode by remember { mutableStateOf(false) }
    var showMatchFoundDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Connect and authenticate on mount
    LaunchedEffect(Unit) {
        if (connectionState != TransportConnectionState.CONNECTED) {
            serverConnectionManager.connect()
        }
    }

    // Set player info when wallet is available
    LaunchedEffect(walletAddress, playerName) {
        if (walletAddress != null) {
            serverConnectionManager.setPlayerInfo(walletAddress, playerName)
        }
    }

    // Handle match found
    LaunchedEffect(matchmakingState) {
        when (matchmakingState) {
            MatchmakingState.MATCH_FOUND -> showMatchFoundDialog = true
            MatchmakingState.IN_GAME -> {
                showMatchFoundDialog = false
                val match = matchInfo
                val game = selectedGame
                if (match != null && game != null) {
                    onStartGame(game, match)
                }
            }
            else -> {}
        }
    }

    // Match found dialog
    if (showMatchFoundDialog && matchInfo != null) {
        MatchFoundDialog(
            matchInfo = matchInfo!!,
            onAccept = {
                serverConnectionManager.acceptMatch(matchInfo!!.gameId)
                showMatchFoundDialog = false
            },
            onDecline = {
                serverConnectionManager.declineMatch(matchInfo!!.gameId)
                showMatchFoundDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = {
                    if (matchmakingState == MatchmakingState.QUEUED) {
                        serverConnectionManager.leaveQueue()
                    }
                    onBack()
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Find Match",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Play against anyone worldwide",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Balance display
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Toll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "%.2f VIIIBE".format(viiibeBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection status
        ConnectionStatusBanner(
            connectionState = connectionState,
            isAuthenticated = isAuthenticated
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { errorMessage = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Check wallet connection
        if (walletAddress == null) {
            WalletRequiredCard()
            return@Column
        }

        // Check version requirements for multiplayer
        if (!versionState.multiplayerAllowed) {
            UpdateRequiredCard(
                currentVersion = currentVersion,
                requiredVersion = versionState.minVersionMultiplayer,
                onOpenDownloadUrl = onOpenDownloadUrl
            )
            return@Column
        }

        // Main content based on state
        when (matchmakingState) {
            MatchmakingState.IDLE, MatchmakingState.ERROR -> {
                GameSelectionView(
                    selectedGame = selectedGame,
                    stakeAmount = stakeAmount,
                    isFriendlyMode = isFriendlyMode,
                    viiibeBalance = viiibeBalance,
                    isConnected = isAuthenticated,
                    onGameSelected = { selectedGame = it },
                    onStakeChanged = { stakeAmount = it },
                    onModeChanged = { isFriendlyMode = it },
                    onFindMatch = {
                        if (selectedGame != null) {
                            val stake = if (isFriendlyMode) null else stakeAmount.toDoubleOrNull()
                            val mode = if (isFriendlyMode) GameMode.FRIENDLY else GameMode.WAGERED
                            serverConnectionManager.joinQueue(
                                gameType = selectedGame!!.name,
                                gameMode = mode,
                                stakeAmount = stake
                            )
                        }
                    }
                )
            }
            MatchmakingState.QUEUED -> {
                SearchingForMatchView(
                    selectedGame = selectedGame,
                    queuePosition = queuePosition,
                    estimatedWaitMs = estimatedWaitMs,
                    onCancel = {
                        serverConnectionManager.leaveQueue()
                    }
                )
            }
            MatchmakingState.MATCH_ACCEPTED -> {
                WaitingForOpponentAcceptView()
            }
            else -> {}
        }
    }
}

@Composable
fun ConnectionStatusBanner(
    connectionState: TransportConnectionState,
    isAuthenticated: Boolean
) {
    val (icon, text, color) = when {
        connectionState == TransportConnectionState.CONNECTED && isAuthenticated -> {
            Triple(Icons.Filled.CloudDone, "Connected to game server", MaterialTheme.colorScheme.primary)
        }
        connectionState == TransportConnectionState.CONNECTED && !isAuthenticated -> {
            Triple(Icons.Filled.CloudSync, "Authenticating...", MaterialTheme.colorScheme.secondary)
        }
        connectionState == TransportConnectionState.CONNECTING || connectionState == TransportConnectionState.IDLE -> {
            Triple(Icons.Filled.CloudSync, "Connecting to server...", MaterialTheme.colorScheme.secondary)
        }
        connectionState == TransportConnectionState.ERROR -> {
            Triple(Icons.Filled.CloudOff, "Connection error - tap to retry", MaterialTheme.colorScheme.error)
        }
        else -> {
            Triple(Icons.Filled.Cloud, "Disconnected", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSelectionView(
    selectedGame: ArcadeGame?,
    stakeAmount: String,
    isFriendlyMode: Boolean,
    viiibeBalance: Double,
    isConnected: Boolean,
    onGameSelected: (ArcadeGame) -> Unit,
    onStakeChanged: (String) -> Unit,
    onModeChanged: (Boolean) -> Unit,
    onFindMatch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Game Mode Selection
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Game Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GameModeCard(
                        title = "Wagered",
                        description = "Stake VIIIBE tokens",
                        icon = Icons.Filled.Toll,
                        isSelected = !isFriendlyMode,
                        modifier = Modifier.weight(1f),
                        onClick = { onModeChanged(false) }
                    )
                    GameModeCard(
                        title = "Friendly",
                        description = "Free to play",
                        icon = Icons.Filled.SportsEsports,
                        isSelected = isFriendlyMode,
                        modifier = Modifier.weight(1f),
                        onClick = { onModeChanged(true) }
                    )
                }
            }
        }

        // Game Type Selection
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Game Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        ArcadeGame.SPRINT_RACE to Icons.Filled.DirectionsRun,
                        ArcadeGame.POWER_WAR to Icons.Filled.FitnessCenter
                    ).forEach { (game, icon) ->
                        GameTypeCard(
                            game = game,
                            icon = icon,
                            isSelected = selectedGame == game,
                            modifier = Modifier.weight(1f),
                            onClick = { onGameSelected(game) }
                        )
                    }
                }
            }
        }

        // Stake Amount (for wagered games)
        if (!isFriendlyMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Stake Amount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = stakeAmount,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                onStakeChanged(newValue)
                            }
                        },
                        label = { Text("VIIIBE") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { onStakeChanged(viiibeBalance.toInt().toString()) }) {
                                Text("MAX")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val stake = stakeAmount.toDoubleOrNull() ?: 0.0
                    if (stake > 0) {
                        Text(
                            text = "Winner takes ${String.format("%.2f", stake * 2 * 0.95)} VIIIBE (5% house fee)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Find Match Button
        val hasValidStake = isFriendlyMode || ((stakeAmount.toDoubleOrNull() ?: 0.0) > 0 &&
                (stakeAmount.toDoubleOrNull() ?: 0.0) <= viiibeBalance)
        val canFindMatch = selectedGame != null && hasValidStake

        Button(
            onClick = {
                if (selectedGame == null) return@Button
                if (!hasValidStake) return@Button
                onFindMatch()
            },
            enabled = canFindMatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (!isConnected) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Connecting...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Find Match",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Show connection hint if not connected
        if (!isConnected && selectedGame != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Waiting for server connection...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun GameModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isSelected) {
            null
        } else {
            ButtonDefaults.outlinedButtonBorder
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GameTypeCard(
    game: ArcadeGame,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Get game duration from the game type
    val durationSeconds = when (game) {
        ArcadeGame.SPRINT_RACE -> 90
        ArcadeGame.POWER_WAR -> 45
        ArcadeGame.RHYTHM_RIDE -> 120
        ArcadeGame.ZOMBIE_ESCAPE -> 120
        ArcadeGame.HILL_CLIMB -> 180
        ArcadeGame.POWER_SURGE -> 90
        ArcadeGame.MUSIC_SPEED -> 180
        ArcadeGame.PAPER_ROUTE -> 120
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = game.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${durationSeconds}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SearchingForMatchView(
    selectedGame: ArcadeGame?,
    queuePosition: Int?,
    estimatedWaitMs: Long?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated searching indicator
        SearchingAnimation()

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Finding Opponent...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        selectedGame?.let {
            Text(
                text = it.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Queue stats
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                queuePosition?.let { pos ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Queue Position: #$pos",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                estimatedWaitMs?.let { waitMs ->
                    val waitSeconds = waitMs / 1000
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Est. wait: ${waitSeconds}s",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel")
        }
    }
}

@Composable
fun SearchingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "searching")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, delayMillis = 333),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    val scale3 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, delayMillis = 666),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale3"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Surface(
            modifier = Modifier
                .size(120.dp)
                .scale(scale3),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {}

        // Middle ring
        Surface(
            modifier = Modifier
                .size(80.dp)
                .scale(scale2),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ) {}

        // Inner circle
        Surface(
            modifier = Modifier
                .size(48.dp)
                .scale(scale1),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ) {}

        // Center icon
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun WaitingForOpponentAcceptView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Match Found!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Waiting for opponent to accept...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MatchFoundDialog(
    matchInfo: MatchInfo,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var timeRemaining by remember { mutableStateOf((matchInfo.acceptTimeoutMs / 1000).toInt()) }

    LaunchedEffect(matchInfo.acceptTimeoutMs) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
        // Auto-decline if timer runs out
        if (timeRemaining <= 0) {
            onDecline()
        }
    }

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Opponent Found!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = matchInfo.opponentName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = matchInfo.gameType,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (matchInfo.stakeAmount != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${matchInfo.stakeAmount} VIIIBE",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Friendly Match",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timer
                Surface(
                    shape = CircleShape,
                    color = if (timeRemaining <= 5) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = "${timeRemaining}s",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (timeRemaining <= 5) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Accept")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Decline")
            }
        }
    )
}

/**
 * Card shown when the app version is too old for multiplayer
 */
@Composable
fun UpdateRequiredCard(
    currentVersion: String,
    requiredVersion: String,
    onOpenDownloadUrl: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Update Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A newer version is required to play multiplayer games.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Your Version",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = currentVersion,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = requiredVersion,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenDownloadUrl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Update")
            }
        }
    }
}
