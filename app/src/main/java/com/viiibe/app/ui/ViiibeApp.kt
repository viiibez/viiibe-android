package com.viiibe.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viiibe.app.arcade.data.AIDifficulty
import com.viiibe.app.arcade.data.ArcadeGame
import com.viiibe.app.arcade.p2p.MatchmakingViewModel
import com.viiibe.app.arcade.ui.ArcadeScreen
import com.viiibe.app.arcade.ui.GamePlayScreen
import com.viiibe.app.auth.AuthViewModel
import com.viiibe.app.blockchain.BlockchainViewModel
import com.viiibe.app.bluetooth.BluetoothViewModel
import com.viiibe.app.data.model.WorkoutVideo
import com.viiibe.app.ui.screens.*
import com.viiibe.app.ui.viewmodel.UserViewModel
import com.viiibe.app.version.VersionViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object ProfileSelect : Screen("profile_select", "Select Profile", Icons.Filled.Person)
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Ride : Screen("ride", "Ride", Icons.Filled.DirectionsBike)
    object Workouts : Screen("workouts", "Workouts", Icons.Filled.PlayCircle)
    object Maps : Screen("maps", "Maps", Icons.Filled.Map)
    object MapRide : Screen("map_ride", "Map Ride", Icons.Filled.Map)
    object Arcade : Screen("arcade", "Arcade", Icons.Filled.SportsEsports)
    object ArcadeGame : Screen("arcade_game/{game}/{difficulty}", "Game", Icons.Filled.SportsEsports)
    object Multiplayer : Screen("multiplayer", "Multiplayer", Icons.Filled.Groups)
    object MultiplayerGame : Screen("multiplayer_game/{game}", "P2P Game", Icons.Filled.Groups)
    object GlobalMatchmaking : Screen("global_matchmaking", "Find Match", Icons.Filled.Public)
    object GlobalMatchGame : Screen("global_match_game/{game}/{gameId}", "Match", Icons.Filled.Public)
    object History : Screen("history", "History", Icons.Filled.History)
    object Wallet : Screen("wallet", "Wallet", Icons.Filled.AccountBalanceWallet)
    object Achievements : Screen("achievements", "NFTs", Icons.Filled.EmojiEvents)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val mainScreens = listOf(
    Screen.Home,
    Screen.Ride,
    Screen.Workouts,
    Screen.Arcade,
    Screen.Multiplayer,
    Screen.History,
    Screen.Wallet,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViiibeApp() {
    val navController = rememberNavController()
    val bluetoothViewModel: BluetoothViewModel = viewModel()
    val userViewModel: UserViewModel = viewModel()
    val blockchainViewModel: BlockchainViewModel = viewModel()
    val matchmakingViewModel: MatchmakingViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val versionViewModel: VersionViewModel = viewModel()

    // Observe version state
    val versionState by versionViewModel.versionState.collectAsState()

    // X OAuth launcher
    val xAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.resultCode == Activity.RESULT_CANCELED) {
            // Handle both OK and CANCELED because the OAuth response comes via intent data
            result.data?.let { intent ->
                authViewModel.handleAuthResponse(intent)
            } ?: run {
                // User cancelled the auth flow
                authViewModel.cancelAuthFlow()
            }
        }
    }

    // Selected video for ride screen
    var selectedVideo by remember { mutableStateOf<WorkoutVideo?>(null) }

    // Selected route for map ride screen
    var selectedRoute by remember { mutableStateOf<VirtualRoute?>(null) }

    // Arcade game selection
    var selectedArcadeGame by remember { mutableStateOf<ArcadeGame?>(null) }
    var selectedDifficulty by remember { mutableStateOf(AIDifficulty.MEDIUM) }

    // Observe if we need profile selection
    val needsProfileSelection by userViewModel.needsProfileSelection.collectAsState()
    val activeUser by userViewModel.activeUser.collectAsState()
    val walletState by blockchainViewModel.walletState.collectAsState()

    // Initialize default user if needed
    LaunchedEffect(Unit) {
        userViewModel.ensureDefaultUser()
    }

    // Update blockchain viewmodel when user changes
    LaunchedEffect(activeUser) {
        activeUser?.let { user ->
            blockchainViewModel.setCurrentUser(user.id)
        }
    }

    // Determine start destination based on user state
    val startDestination = if (needsProfileSelection) {
        Screen.ProfileSelect.route
    } else {
        Screen.Home.route
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Don't show nav rail on profile selection screen
    val showNavRail = currentRoute != Screen.ProfileSelect.route

    Row(modifier = Modifier.fillMaxSize()) {
        // Side navigation rail for landscape tablet
        if (showNavRail) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val currentDestination = navBackStackEntry?.destination

                Spacer(modifier = Modifier.weight(1f))

                mainScreens.forEach { screen ->
                    NavigationRailItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Main content
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.ProfileSelect.route) {
                ProfileSelectionScreen(
                    userViewModel = userViewModel,
                    onProfileSelected = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.ProfileSelect.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    bluetoothViewModel = bluetoothViewModel,
                    userViewModel = userViewModel,
                    versionState = versionState,
                    onOpenDownloadUrl = { versionViewModel.openDownloadUrl() },
                    onStartRide = {
                        selectedVideo = null  // Free ride (no video)
                        navController.navigate(Screen.Ride.route)
                    },
                    onBrowseWorkouts = { navController.navigate(Screen.Workouts.route) },
                    onSwitchProfile = {
                        navController.navigate(Screen.ProfileSelect.route)
                    }
                )
            }
            composable(Screen.Ride.route) {
                RideScreen(
                    bluetoothViewModel = bluetoothViewModel,
                    userViewModel = userViewModel,
                    videoUrl = selectedVideo?.videoUrl,
                    videoTitle = selectedVideo?.title,
                    onEndRide = {
                        selectedVideo = null  // Clear selected video when ride ends
                        navController.navigate(Screen.Home.route)
                    }
                )
            }
            composable(Screen.Workouts.route) {
                WorkoutsScreen(
                    onSelectWorkout = { video ->
                        selectedVideo = video  // Store selected video
                        navController.navigate(Screen.Ride.route)
                    }
                )
            }
            composable(Screen.Maps.route) {
                MapsScreen(
                    bluetoothViewModel = bluetoothViewModel,
                    userViewModel = userViewModel,
                    onStartRoute = { route ->
                        selectedRoute = route
                        navController.navigate(Screen.MapRide.route)
                    }
                )
            }
            composable(Screen.MapRide.route) {
                selectedRoute?.let { route ->
                    MapRideScreen(
                        bluetoothViewModel = bluetoothViewModel,
                        userViewModel = userViewModel,
                        route = route,
                        onEndRide = {
                            selectedRoute = null
                            navController.navigate(Screen.Home.route)
                        }
                    )
                }
            }
            composable(Screen.Arcade.route) {
                ArcadeScreen(
                    onSelectGame = { game, difficulty ->
                        selectedArcadeGame = game
                        selectedDifficulty = difficulty
                        navController.navigate("arcade_game/${game.name}/${difficulty.name}")
                    }
                )
            }
            composable(Screen.ArcadeGame.route) { backStackEntry ->
                val gameName = backStackEntry.arguments?.getString("game") ?: ArcadeGame.SPRINT_RACE.name
                val difficultyName = backStackEntry.arguments?.getString("difficulty") ?: AIDifficulty.MEDIUM.name
                val game = ArcadeGame.valueOf(gameName)
                val difficulty = AIDifficulty.valueOf(difficultyName)

                GamePlayScreen(
                    game = game,
                    difficulty = difficulty,
                    bluetoothViewModel = bluetoothViewModel,
                    userId = activeUser?.id ?: 0,
                    walletAddress = walletState.address,
                    statsSyncAllowed = versionState.statsSyncAllowed,
                    onExitGame = {
                        navController.popBackStack()
                    }
                )
            }
            // Multiplayer now navigates directly to global matchmaking
            // Local P2P has been removed to prevent cheating
            composable(Screen.Multiplayer.route) {
                val walletAddress by matchmakingViewModel.walletAddress.collectAsState()
                val viiibeBalance by matchmakingViewModel.viiibeBalance.collectAsState()
                val playerName by matchmakingViewModel.playerName.collectAsState()

                MatchmakingScreen(
                    serverConnectionManager = matchmakingViewModel.serverConnection,
                    viiibeBalance = viiibeBalance,
                    walletAddress = walletAddress,
                    playerName = playerName,
                    versionState = versionState,
                    currentVersion = versionViewModel.currentVersion,
                    onOpenDownloadUrl = { versionViewModel.openDownloadUrl() },
                    onStartGame = { game, matchInfo ->
                        navController.navigate("global_match_game/${game.name}/${matchInfo.gameId}")
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.MultiplayerGame.route) { backStackEntry ->
                val gameName = backStackEntry.arguments?.getString("game") ?: ArcadeGame.SPRINT_RACE.name
                val game = ArcadeGame.valueOf(gameName)

                // Use the regular GamePlayScreen for P2P games too
                // The P2PViewModel will handle the multiplayer state
                GamePlayScreen(
                    game = game,
                    difficulty = AIDifficulty.MEDIUM, // Not used in P2P
                    bluetoothViewModel = bluetoothViewModel,
                    userId = activeUser?.id ?: 0,
                    walletAddress = walletState.address,
                    statsSyncAllowed = versionState.statsSyncAllowed,
                    onExitGame = {
                        navController.popBackStack()
                    }
                )
            }
            // Global Matchmaking Screen
            composable(Screen.GlobalMatchmaking.route) {
                val walletAddress by matchmakingViewModel.walletAddress.collectAsState()
                val viiibeBalance by matchmakingViewModel.viiibeBalance.collectAsState()
                val playerName by matchmakingViewModel.playerName.collectAsState()

                MatchmakingScreen(
                    serverConnectionManager = matchmakingViewModel.serverConnection,
                    viiibeBalance = viiibeBalance,
                    walletAddress = walletAddress,
                    playerName = playerName,
                    versionState = versionState,
                    currentVersion = versionViewModel.currentVersion,
                    onOpenDownloadUrl = { versionViewModel.openDownloadUrl() },
                    onStartGame = { game, matchInfo ->
                        navController.navigate("global_match_game/${game.name}/${matchInfo.gameId}")
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            // Global Match Game Screen
            composable(Screen.GlobalMatchGame.route) { backStackEntry ->
                val gameName = backStackEntry.arguments?.getString("game") ?: ArcadeGame.SPRINT_RACE.name
                val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
                val game = ArcadeGame.valueOf(gameName)

                // Use GamePlayScreen with matchmaking ViewModel for server-relayed games
                GamePlayScreen(
                    game = game,
                    difficulty = AIDifficulty.MEDIUM,
                    bluetoothViewModel = bluetoothViewModel,
                    userId = activeUser?.id ?: 0,
                    walletAddress = walletState.address,
                    statsSyncAllowed = versionState.statsSyncAllowed,
                    onExitGame = {
                        matchmakingViewModel.resetGameState()
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    userViewModel = userViewModel
                )
            }
            composable(Screen.Wallet.route) {
                WalletScreen(
                    blockchainViewModel = blockchainViewModel,
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route)
                    }
                )
            }
            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    blockchainViewModel = blockchainViewModel,
                    userViewModel = userViewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    bluetoothViewModel = bluetoothViewModel,
                    userViewModel = userViewModel,
                    authViewModel = authViewModel,
                    onSwitchProfile = {
                        navController.navigate(Screen.ProfileSelect.route)
                    },
                    onLinkXAccount = {
                        val authIntent = authViewModel.buildAuthIntent()
                        xAuthLauncher.launch(authIntent)
                    }
                )
            }
        }
    }
}
