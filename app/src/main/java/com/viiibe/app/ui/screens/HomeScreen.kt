package com.viiibe.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.viiibe.app.bluetooth.BluetoothViewModel
import com.viiibe.app.data.model.AvatarColors
import com.viiibe.app.data.repository.PlayerRepository
import com.viiibe.app.ui.viewmodel.UserViewModel
import com.viiibe.app.version.VersionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bluetoothViewModel: BluetoothViewModel,
    userViewModel: UserViewModel,
    miniStats: PlayerRepository.MiniStats? = null,
    versionState: VersionState = VersionState(),
    onOpenDownloadUrl: () -> Unit = {},
    onStartRide: () -> Unit,
    onBrowseWorkouts: () -> Unit,
    onSwitchProfile: () -> Unit,
    onViewStats: () -> Unit = {}
) {
    val connectionState by bluetoothViewModel.connectionState.collectAsState()
    val activeUser by userViewModel.activeUser.collectAsState()

    // Bluetooth permissions
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            bluetoothViewModel.startScanning()
        }
    }

    // Get stats for active user
    val userId = activeUser?.id ?: 0L
    val totalWorkouts by userViewModel.getWorkoutCountForUser(userId).collectAsState(initial = 0)
    val totalMinutes by userViewModel.getTotalMinutesForUser(userId).collectAsState(initial = 0)
    val totalOutput by userViewModel.getTotalOutputForUser(userId).collectAsState(initial = 0)
    val totalCalories by userViewModel.getTotalCaloriesForUser(userId).collectAsState(initial = 0)

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
            Column {
                // Greeting with user name
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = activeUser?.name ?: "Rider",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection status
                ConnectionStatusChip(
                    isConnected = connectionState.isConnected,
                    deviceName = connectionState.deviceName,
                    isScanning = connectionState.isScanning,
                    onConnect = { permissionLauncher.launch(bluetoothPermissions) },
                    onDisconnect = { bluetoothViewModel.disconnect() }
                )

                // Profile avatar button - show X profile picture if linked
                activeUser?.let { user ->
                    Surface(
                        onClick = onSwitchProfile,
                        shape = CircleShape,
                        color = if (miniStats?.xProfilePicture == null) {
                            Color(AvatarColors.getColor(user.avatarColor))
                        } else {
                            Color.Transparent
                        }
                    ) {
                        if (miniStats?.xProfilePicture != null) {
                            AsyncImage(
                                model = miniStats.xProfilePicture,
                                contentDescription = "Profile picture",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Update available banner (non-blocking)
        if (versionState.updateAvailable) {
            UpdateAvailableBanner(
                latestVersion = versionState.latestVersion,
                releaseNotes = versionState.releaseNotes,
                onDownload = onOpenDownloadUrl
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Ride Button
            GradientActionButton(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
                title = "Quick Ride",
                subtitle = "Start a free ride",
                icon = Icons.Filled.DirectionsBike,
                gradientColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = onStartRide
            )

            // Browse Workouts Button
            GradientActionButton(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
                title = "Workouts",
                subtitle = "Browse guided rides",
                icon = Icons.Filled.PlayCircle,
                gradientColors = listOf(
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.secondaryContainer
                ),
                onClick = onBrowseWorkouts
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Stats cards
        Text(
            text = "Your Stats",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Workouts",
                value = totalWorkouts.toString(),
                icon = Icons.Filled.FitnessCenter
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Minutes",
                value = ((totalMinutes ?: 0) / 60).toString(),
                icon = Icons.Filled.Timer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Output",
                value = "${(totalOutput ?: 0) / 1000}kJ",
                icon = Icons.Filled.Bolt
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Calories",
                value = (totalCalories ?: 0).toString(),
                icon = Icons.Filled.LocalFireDepartment
            )
        }

        // Game Stats Card (if player has game stats)
        if (miniStats != null && miniStats.gamesPlayed > 0) {
            Spacer(modifier = Modifier.height(24.dp))

            GameStatsCard(
                miniStats = miniStats,
                onClick = onViewStats
            )
        }
    }
}

/**
 * Mini game stats card for home screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStatsCard(
    miniStats: PlayerRepository.MiniStats,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trophy icon or profile picture
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "Arcade Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "${miniStats.gamesPlayed} games | ${miniStats.winRate.toInt()}% win rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Earnings badge
            if (miniStats.totalEarnings > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = formatEthShort(miniStats.totalEarnings),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "View stats",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * Format ETH amount for compact display
 */
private fun formatEthShort(amount: Double): String {
    return if (amount >= 1.0) {
        String.format("%.2f AVAX", amount)
    } else if (amount >= 0.01) {
        String.format("%.4f AVAX", amount)
    } else if (amount > 0) {
        String.format("%.6f AVAX", amount)
    } else {
        "0 AVAX"
    }
}

@Composable
fun ConnectionStatusChip(
    isConnected: Boolean,
    deviceName: String?,
    isScanning: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        onClick = if (isConnected) onDisconnect else onConnect,
        shape = RoundedCornerShape(24.dp),
        color = if (isConnected) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Scanning...",
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.BluetoothSearching,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isConnected) deviceName ?: "Connected" else "Connect Bike",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GradientActionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(gradientColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Non-blocking banner to notify user that an update is available
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableBanner(
    latestVersion: String,
    releaseNotes: String?,
    onDownload: () -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }

    if (dismissed) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Version $latestVersion is now available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            TextButton(
                onClick = onDownload,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Update", fontWeight = FontWeight.SemiBold)
            }

            IconButton(
                onClick = { dismissed = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
