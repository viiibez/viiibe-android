package com.viiibe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.viiibe.app.auth.AuthViewModel
import com.viiibe.app.auth.XAuthManager
import com.viiibe.app.bluetooth.BluetoothViewModel
import com.viiibe.app.data.model.AvatarColors
import com.viiibe.app.data.model.User
import com.viiibe.app.ui.viewmodel.UserViewModel
import com.viiibe.app.util.AppVersionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bluetoothViewModel: BluetoothViewModel,
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    onSwitchProfile: () -> Unit,
    onLinkXAccount: () -> Unit
) {
    val connectionState by bluetoothViewModel.connectionState.collectAsState()
    val activeUser by userViewModel.activeUser.collectAsState()
    val allUsers by userViewModel.allUsers.collectAsState()
    val xAuthState by authViewModel.authState.collectAsState()

    // Edit profile dialog state
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showUnlinkXConfirmDialog by remember { mutableStateOf(false) }

    // Edit profile dialog
    if (showEditProfileDialog && activeUser != null) {
        CreateProfileDialog(
            onDismiss = { showEditProfileDialog = false },
            onCreate = { name, colorIndex ->
                userViewModel.updateUserName(activeUser!!.id, name)
                userViewModel.updateUserColor(activeUser!!.id, colorIndex)
                showEditProfileDialog = false
            },
            initialName = activeUser!!.name,
            initialColorIndex = activeUser!!.avatarColor,
            title = "Edit Profile"
        )
    }

    // Add profile dialog
    if (showAddProfileDialog) {
        CreateProfileDialog(
            onDismiss = { showAddProfileDialog = false },
            onCreate = { name, colorIndex ->
                userViewModel.createUser(name, colorIndex, setActive = false)
                showAddProfileDialog = false
            },
            title = "Add Profile"
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog && activeUser != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Profile?") },
            text = {
                Text("Are you sure you want to delete ${activeUser!!.name}'s profile? All workout history will be permanently deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        userViewModel.deleteUser(activeUser!!)
                        showDeleteConfirmDialog = false
                        onSwitchProfile()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unlink X account confirmation dialog
    if (showUnlinkXConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkXConfirmDialog = false },
            title = { Text("Unlink X Account?") },
            text = {
                Text("Are you sure you want to unlink your X account (@${xAuthState.profile?.username ?: ""})? You can link it again later.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.logout()
                        showUnlinkXConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkXConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Current Profile Section
        SettingsSection(title = "Current Profile") {
            activeUser?.let { user ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Surface(
                            shape = CircleShape,
                            color = Color(AvatarColors.getColor(user.avatarColor))
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Active profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Edit button
                        IconButton(onClick = { showEditProfileDialog = true }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit profile"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Switch profile button
            OutlinedButton(
                onClick = onSwitchProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.SwitchAccount, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch Profile")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add profile button
            OutlinedButton(
                onClick = { showAddProfileDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add New Profile")
            }

            // Delete profile (only if more than one user)
            if (allUsers.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete This Profile")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bike Connection Section
        SettingsSection(title = "Bike Connection") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (connectionState.isConnected) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (connectionState.isConnected)
                                Icons.Filled.BluetoothConnected
                            else
                                Icons.Filled.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (connectionState.isConnected)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = if (connectionState.isConnected)
                                    connectionState.deviceName ?: "Connected"
                                else
                                    "Not Connected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (connectionState.isConnected)
                                    "Bluetooth connected"
                                else
                                    "Tap to scan for bikes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (connectionState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = {
                                if (connectionState.isConnected) {
                                    bluetoothViewModel.disconnect()
                                } else {
                                    bluetoothViewModel.startScanning()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (connectionState.isConnected)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (connectionState.isConnected) "Disconnect" else "Connect"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Units Section
        SettingsSection(title = "Units") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
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
                            text = "Distance & Speed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (activeUser?.useMetricUnits == true) "Kilometers (km, km/h)" else "Miles (mi, mph)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "mi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (activeUser?.useMetricUnits != true) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeUser?.useMetricUnits != true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = activeUser?.useMetricUnits == true,
                            onCheckedChange = { useMetric ->
                                activeUser?.let { user ->
                                    userViewModel.updateUserSettings(
                                        userId = user.id,
                                        useMetricUnits = useMetric
                                    )
                                }
                            }
                        )
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (activeUser?.useMetricUnits == true) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeUser?.useMetricUnits == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // X Account Section
        SettingsSection(title = "X Account") {
            XAccountCard(
                xAuthState = xAuthState,
                onLinkAccount = onLinkXAccount,
                onUnlinkAccount = { showUnlinkXConfirmDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Heart Rate Zones
        SettingsSection(title = "Heart Rate Zones") {
            HRZoneItem(zone = 1, range = "50-60%", label = "Recovery", color = MaterialTheme.colorScheme.tertiary)
            HRZoneItem(zone = 2, range = "60-70%", label = "Endurance", color = MaterialTheme.colorScheme.primary)
            HRZoneItem(zone = 3, range = "70-80%", label = "Tempo", color = MaterialTheme.colorScheme.secondary)
            HRZoneItem(zone = 4, range = "80-90%", label = "Threshold", color = MaterialTheme.colorScheme.error)
            HRZoneItem(zone = 5, range = "90-100%", label = "Max", color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About Section
        SettingsSection(title = "About") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "FreeSpin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = AppVersionInfo.getVersionDisplayString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "A free, open-source cycling companion app with multi-user support. Track your rides, watch workout videos, and improve your fitness - all without a subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Open Source") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text("Multi-User") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        // Version info at the bottom
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = AppVersionInfo.getVersionDisplayString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun HRZoneItem(
    zone: Int,
    range: String,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color
                ) {
                    Text(
                        text = "Z$zone",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = range,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

/**
 * X Account card showing linked status or link button
 */
@Composable
fun XAccountCard(
    xAuthState: AuthViewModel.XAuthState,
    onLinkAccount: () -> Unit,
    onUnlinkAccount: () -> Unit
) {
    // X brand color
    val xBrandColor = Color(0xFF000000)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        when {
            xAuthState.isLoading -> {
                // Loading state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Connecting to X...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            xAuthState.isAuthenticated && xAuthState.profile != null -> {
                // Linked state - show profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile picture
                    if (xAuthState.profile.profileImageUrl != null) {
                        AsyncImage(
                            model = xAuthState.profile.profileImageUrl,
                            contentDescription = "X profile picture",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback avatar
                        Surface(
                            shape = CircleShape,
                            color = xBrandColor
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = xAuthState.profile.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Profile info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = xAuthState.profile.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "@${xAuthState.profile.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Linked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Unlink button
                    TextButton(
                        onClick = onUnlinkAccount,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlink")
                    }
                }
            }
            else -> {
                // Not linked state - direct to website
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // X logo placeholder
                        Surface(
                            shape = CircleShape,
                            color = xBrandColor
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "X",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link your X account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Display your profile picture and username",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Website instruction card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Computer,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Visit the website to link",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "viiibe.com/profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sign in with your wallet address, then link your X account. Your profile will sync automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
