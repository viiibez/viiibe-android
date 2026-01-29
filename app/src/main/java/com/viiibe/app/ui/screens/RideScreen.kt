package com.viiibe.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.viiibe.app.ViiibeApplication
import com.viiibe.app.bluetooth.BikeConnectionService
import com.viiibe.app.bluetooth.BluetoothViewModel
import com.viiibe.app.data.model.RideMetrics
import com.viiibe.app.data.model.Workout
import com.viiibe.app.data.model.WorkoutSample
import com.viiibe.app.ui.components.BottomMetricsBar
import com.viiibe.app.ui.viewmodel.UserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideScreen(
    bluetoothViewModel: BluetoothViewModel,
    userViewModel: UserViewModel,
    videoUrl: String? = null,
    videoTitle: String? = null,
    onEndRide: () -> Unit
) {
    val metrics by bluetoothViewModel.metrics.collectAsState()
    val connectionState by bluetoothViewModel.connectionState.collectAsState()
    val activeUser by userViewModel.activeUser.collectAsState()

    var isRiding by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var showEndRideDialog by remember { mutableStateOf(false) }
    var isInOverlayMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workoutDao = ViiibeApplication.instance.database.workoutDao()

    var currentWorkoutId by remember { mutableStateOf<Long?>(null) }
    var rideStartTime by remember { mutableStateOf(0L) }

    // Sample collection for detailed ride data
    val samples = remember { mutableListOf<WorkoutSample>() }

    // Elapsed time counter
    var elapsedSeconds by remember { mutableStateOf(0) }

    // Track max values
    var maxCadence by remember { mutableStateOf(0) }
    var maxHeartRate by remember { mutableStateOf(0) }
    var maxOutput by remember { mutableStateOf(0) }

    val useMetricUnits = activeUser?.useMetricUnits == true

    // Get bike service to sync elapsed time
    val bikeService = (context.applicationContext as? ViiibeApplication)?.bikeService

    // Lifecycle observer to sync time and pause state when returning from overlay mode
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isInOverlayMode) {
                // Sync elapsed time from overlay service
                bikeService?.getOverlayElapsedSeconds()?.let { overlayTime ->
                    elapsedSeconds = overlayTime
                }
                // Sync pause state from overlay service
                bikeService?.isWorkoutPaused()?.let { overlayPaused ->
                    isPaused = overlayPaused
                    if (isPaused) {
                        bluetoothViewModel.pauseRide()
                    } else {
                        bluetoothViewModel.resumeRide()
                    }
                }
                isInOverlayMode = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Timer effect - don't run when in overlay mode (overlay has its own timer)
    LaunchedEffect(isRiding, isPaused, isInOverlayMode) {
        if (isRiding && !isPaused && !isInOverlayMode) {
            while (true) {
                delay(1000)
                elapsedSeconds++

                // Update max values
                if (metrics.cadence > maxCadence) maxCadence = metrics.cadence
                if (metrics.heartRate > maxHeartRate) maxHeartRate = metrics.heartRate
                if (metrics.power > maxOutput) maxOutput = metrics.power

                // Send updates to overlay if in overlay mode
                if (isInOverlayMode) {
                    val updateIntent = Intent(context, BikeConnectionService::class.java).apply {
                        action = BikeConnectionService.ACTION_UPDATE_OVERLAY_METRICS
                        putExtra(BikeConnectionService.EXTRA_ELAPSED_SECONDS, elapsedSeconds)
                        putExtra(BikeConnectionService.EXTRA_POWER, metrics.power)
                        putExtra(BikeConnectionService.EXTRA_CADENCE, metrics.cadence)
                        putExtra(BikeConnectionService.EXTRA_RESISTANCE, metrics.resistance)
                        putExtra(BikeConnectionService.EXTRA_HEART_RATE, metrics.heartRate)
                        putExtra(BikeConnectionService.EXTRA_CALORIES, metrics.calories)
                        putExtra(BikeConnectionService.EXTRA_SPEED, metrics.speed)
                        putExtra(BikeConnectionService.EXTRA_DISTANCE, metrics.distance)
                    }
                    context.startService(updateIntent)
                }

                // Collect sample every 5 seconds
                if (elapsedSeconds % 5 == 0 && currentWorkoutId != null) {
                    samples.add(
                        WorkoutSample(
                            workoutId = currentWorkoutId!!,
                            timestamp = System.currentTimeMillis(),
                            cadence = metrics.cadence,
                            resistance = metrics.resistance,
                            power = metrics.power,
                            speed = metrics.speed,
                            heartRate = metrics.heartRate,
                            distance = metrics.distance
                        )
                    )
                }
            }
        }
    }

    fun startRide() {
        isRiding = true
        isPaused = false
        elapsedSeconds = 0
        rideStartTime = System.currentTimeMillis()
        bluetoothViewModel.resetRideMetrics()
        bluetoothViewModel.startRide()

        // Create workout entry for active user
        scope.launch {
            activeUser?.let { user ->
                currentWorkoutId = workoutDao.insertWorkout(
                    Workout(
                        userId = user.id,
                        startTime = rideStartTime,
                        videoTitle = videoTitle,
                        videoUrl = videoUrl
                    )
                )
            }
        }
    }

    fun endRide() {
        isRiding = false
        bluetoothViewModel.pauseRide()

        // Save workout data
        scope.launch {
            currentWorkoutId?.let { id ->
                val workout = workoutDao.getWorkoutById(id)
                workout?.let {
                    workoutDao.updateWorkout(
                        it.copy(
                            endTime = System.currentTimeMillis(),
                            durationSeconds = elapsedSeconds,
                            totalOutput = metrics.totalOutput,
                            avgCadence = if (samples.isNotEmpty()) samples.map { s -> s.cadence }.average().toInt() else metrics.cadence,
                            avgResistance = if (samples.isNotEmpty()) samples.map { s -> s.resistance }.average().toInt() else metrics.resistance,
                            avgSpeed = if (samples.isNotEmpty()) samples.map { s -> s.speed }.average().toFloat() else metrics.speed,
                            avgHeartRate = if (samples.isNotEmpty()) samples.filter { s -> s.heartRate > 0 }.map { s -> s.heartRate }.average().toInt() else metrics.heartRate,
                            maxHeartRate = maxHeartRate,
                            maxCadence = maxCadence,
                            maxOutput = maxOutput,
                            totalDistance = metrics.distance,
                            caloriesBurned = metrics.calories
                        )
                    )

                    // Save samples
                    if (samples.isNotEmpty()) {
                        workoutDao.insertSamples(samples.toList())
                    }
                }
            }
            onEndRide()
        }
    }

    fun minimizeToOverlay() {
        // Check overlay permission first
        if (!android.provider.Settings.canDrawOverlays(context)) {
            // Request overlay permission
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            return
        }

        // Start foreground service with metrics notification
        val serviceIntent = Intent(context, BikeConnectionService::class.java).apply {
            action = BikeConnectionService.ACTION_START_OVERLAY
            putExtra(BikeConnectionService.EXTRA_ELAPSED_SECONDS, elapsedSeconds)
            putExtra(BikeConnectionService.EXTRA_IS_PAUSED, isPaused)
            putExtra(BikeConnectionService.EXTRA_POWER, metrics.power)
            putExtra(BikeConnectionService.EXTRA_CADENCE, metrics.cadence)
            putExtra(BikeConnectionService.EXTRA_RESISTANCE, metrics.resistance)
            putExtra(BikeConnectionService.EXTRA_HEART_RATE, metrics.heartRate)
            putExtra(BikeConnectionService.EXTRA_CALORIES, metrics.calories)
            putExtra(BikeConnectionService.EXTRA_SPEED, metrics.speed)
            putExtra(BikeConnectionService.EXTRA_DISTANCE, metrics.distance)
        }
        context.startService(serviceIntent)

        // Track overlay mode for periodic updates
        isInOverlayMode = true

        // Move app to background
        (context as? Activity)?.moveTaskToBack(true)
    }

    // End ride confirmation dialog
    if (showEndRideDialog) {
        AlertDialog(
            onDismissRequest = { showEndRideDialog = false },
            title = { Text("End Ride?") },
            text = { Text("Are you sure you want to end this ride? Your progress will be saved.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndRideDialog = false
                        endRide()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("End Ride")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndRideDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }

    // Full screen layout with overlays
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video content (full screen)
        if (videoUrl != null) {
            VideoPlayer(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder when no video - free ride mode
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Free Ride",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No video selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Timer overlay (top-left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            Text(
                text = formatTime(elapsedSeconds),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        // Connection indicator (top-right)
        if (!connectionState.isConnected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Text(
                        text = "Bike not connected",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom metrics bar
        BottomMetricsBar(
            metrics = metrics,
            elapsedSeconds = elapsedSeconds,
            isRiding = isRiding,
            isPaused = isPaused,
            useMetricUnits = useMetricUnits,
            onStartRide = { startRide() },
            onPauseResume = {
                isPaused = !isPaused
                if (isPaused) {
                    bluetoothViewModel.pauseRide()
                } else {
                    bluetoothViewModel.resumeRide()
                }
            },
            onEndRide = { showEndRideDialog = true },
            onMinimize = { minimizeToOverlay() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Check if it's a YouTube URL
    val isYouTube = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")

    if (isYouTube) {
        // Open in external app (NewPipe) since Peloton lacks Google Play Services
        val videoId = extractYouTubeVideoId(videoUrl)
        YouTubeEmbeddedPlayer(
            videoId = videoId,
            modifier = modifier
        )
    } else {
        // Use ExoPlayer for direct video URLs
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun YouTubeEmbeddedPlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    // Embed YouTube using WebView with iframe
    val embedHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; }
                html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <iframe
                src="https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1&playsinline=1"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen>
            </iframe>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.BLACK)
                webChromeClient = android.webkit.WebChromeClient()
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    embedHtml,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        modifier = modifier
    )
}

private fun extractYouTubeVideoId(url: String): String {
    // Handle various YouTube URL formats
    val patterns = listOf(
        "(?:youtube\\.com/watch\\?v=|youtu\\.be/)([^&\\s]+)".toRegex(),
        "youtube\\.com/embed/([^?&\\s]+)".toRegex()
    )

    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) {
            return match.groupValues[1]
        }
    }

    return url // Return as-is if no pattern matches
}

private fun formatTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
