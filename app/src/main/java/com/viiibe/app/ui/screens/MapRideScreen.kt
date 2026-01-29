package com.viiibe.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viiibe.app.ViiibeApplication
import com.viiibe.app.bluetooth.BikeConnectionService
import com.viiibe.app.bluetooth.BluetoothViewModel
import com.viiibe.app.data.model.Workout
import com.viiibe.app.ui.components.BottomMetricsBar
import com.viiibe.app.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Data class representing a virtual route for map-based riding
 */
data class VirtualRoute(
    val name: String,
    val startAddress: String,
    val endAddress: String,
    val distanceMeters: Float,
    val waypoints: List<LatLng>,
    val elevationGain: Int = 0 // meters
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapRideScreen(
    bluetoothViewModel: BluetoothViewModel,
    userViewModel: UserViewModel,
    route: VirtualRoute,
    onEndRide: () -> Unit
) {
    val metrics by bluetoothViewModel.metrics.collectAsState()
    val connectionState by bluetoothViewModel.connectionState.collectAsState()
    val activeUser by userViewModel.activeUser.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workoutDao = ViiibeApplication.instance.database.workoutDao()

    var isRiding by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var showEndRideDialog by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var currentWorkoutId by remember { mutableStateOf<Long?>(null) }

    // Virtual position tracking
    var virtualDistanceTraveled by remember { mutableStateOf(0f) } // meters
    var currentPositionIndex by remember { mutableStateOf(0) }

    val useMetricUnits = activeUser?.useMetricUnits == true

    // Calculate progress percentage
    val progressPercent = if (route.distanceMeters > 0) {
        (virtualDistanceTraveled / route.distanceMeters * 100).coerceIn(0f, 100f)
    } else 0f

    // Calculate current position on route based on distance traveled
    val currentPosition = remember(virtualDistanceTraveled, route.waypoints) {
        if (route.waypoints.isEmpty()) return@remember LatLng(0.0, 0.0)

        var accumulated = 0f
        for (i in 0 until route.waypoints.size - 1) {
            val segmentDistance = calculateDistance(route.waypoints[i], route.waypoints[i + 1])
            if (accumulated + segmentDistance >= virtualDistanceTraveled) {
                // Interpolate position within this segment
                val segmentProgress = (virtualDistanceTraveled - accumulated) / segmentDistance
                return@remember interpolatePosition(
                    route.waypoints[i],
                    route.waypoints[i + 1],
                    segmentProgress.coerceIn(0f, 1f)
                )
            }
            accumulated += segmentDistance
        }
        route.waypoints.last()
    }

    // Track if the map is fully loaded
    var isMapLoaded by remember { mutableStateOf(false) }

    // Camera state for the map
    val cameraPositionState = rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            if (route.waypoints.isNotEmpty()) route.waypoints.first() else LatLng(40.7128, -74.0060),
            14f
        )
    }

    // Update camera to follow virtual position - only after map is loaded
    LaunchedEffect(currentPosition, isMapLoaded) {
        if (isMapLoaded && isRiding && !isPaused) {
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLng(currentPosition),
                    durationMs = 1000
                )
            } catch (e: Exception) {
                // CameraUpdateFactory not ready yet, ignore
            }
        }
    }

    // Timer and distance tracking
    LaunchedEffect(isRiding, isPaused) {
        if (isRiding && !isPaused) {
            while (true) {
                delay(1000)
                elapsedSeconds++

                // Update virtual distance based on speed (convert mph to m/s, then to meters per second)
                val speedMps = metrics.speed * 0.44704f // mph to m/s
                virtualDistanceTraveled += speedMps

                // Check if ride is complete
                if (virtualDistanceTraveled >= route.distanceMeters) {
                    // Route completed!
                    showEndRideDialog = true
                }
            }
        }
    }

    // Calculate target cadence based on terrain (simplified - could be enhanced with elevation data)
    val targetCadence = remember(progressPercent) {
        // Base cadence of 80, adjusted for simulated terrain
        val terrainFactor = when {
            progressPercent < 20 -> 1.0f  // Warmup - normal
            progressPercent < 40 -> 1.1f  // Slight incline - higher cadence
            progressPercent < 60 -> 0.85f // Climb - lower cadence, more resistance
            progressPercent < 80 -> 1.15f // Descent - higher cadence
            else -> 1.0f  // Cool down
        }
        (80 * terrainFactor).toInt()
    }

    val targetResistance = remember(progressPercent) {
        when {
            progressPercent < 20 -> 25  // Warmup
            progressPercent < 40 -> 35  // Building
            progressPercent < 60 -> 50  // Climb
            progressPercent < 80 -> 20  // Descent
            else -> 30  // Cool down
        }
    }

    fun startRide() {
        isRiding = true
        isPaused = false
        elapsedSeconds = 0
        virtualDistanceTraveled = 0f
        bluetoothViewModel.resetRideMetrics()
        bluetoothViewModel.startRide()

        scope.launch {
            activeUser?.let { user ->
                currentWorkoutId = workoutDao.insertWorkout(
                    Workout(
                        userId = user.id,
                        startTime = System.currentTimeMillis(),
                        workoutType = "virtual_route",
                        videoTitle = route.name
                    )
                )
            }
        }
    }

    fun endRide() {
        isRiding = false
        bluetoothViewModel.pauseRide()

        scope.launch {
            currentWorkoutId?.let { id ->
                val workout = workoutDao.getWorkoutById(id)
                workout?.let {
                    workoutDao.updateWorkout(
                        it.copy(
                            endTime = System.currentTimeMillis(),
                            durationSeconds = elapsedSeconds,
                            totalOutput = metrics.totalOutput,
                            avgCadence = metrics.cadence,
                            totalDistance = virtualDistanceTraveled / 1609.34f, // Convert to miles
                            caloriesBurned = metrics.calories
                        )
                    )
                }
            }
            onEndRide()
        }
    }

    fun minimizeToOverlay() {
        // Check overlay permission first
        if (!android.provider.Settings.canDrawOverlays(context)) {
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
            putExtra(BikeConnectionService.EXTRA_POWER, metrics.power)
            putExtra(BikeConnectionService.EXTRA_CADENCE, metrics.cadence)
            putExtra(BikeConnectionService.EXTRA_RESISTANCE, metrics.resistance)
            putExtra(BikeConnectionService.EXTRA_HEART_RATE, metrics.heartRate)
            putExtra(BikeConnectionService.EXTRA_CALORIES, metrics.calories)
            putExtra(BikeConnectionService.EXTRA_SPEED, metrics.speed)
            putExtra(BikeConnectionService.EXTRA_DISTANCE, metrics.distance)
        }
        context.startService(serviceIntent)

        // Move app to background
        (context as? Activity)?.moveTaskToBack(true)
    }

    // Auto-start on launch
    LaunchedEffect(Unit) {
        delay(500)
        startRide()
    }

    // End ride dialog
    if (showEndRideDialog) {
        AlertDialog(
            onDismissRequest = { showEndRideDialog = false },
            title = {
                Text(
                    if (virtualDistanceTraveled >= route.distanceMeters) "Route Complete!" else "End Ride?"
                )
            },
            text = {
                Column {
                    if (virtualDistanceTraveled >= route.distanceMeters) {
                        Text("Congratulations! You completed the ${route.name} route!")
                    } else {
                        Text("Are you sure you want to end this ride?")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Distance: ${String.format("%.2f", virtualDistanceTraveled / 1609.34f)} mi")
                    Text("Time: ${formatTime(elapsedSeconds)}")
                    Text("Calories: ${metrics.calories}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showEndRideDialog = false
                    endRide()
                }) {
                    Text("End Ride")
                }
            },
            dismissButton = {
                if (virtualDistanceTraveled < route.distanceMeters) {
                    TextButton(onClick = { showEndRideDialog = false }) {
                        Text("Continue")
                    }
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
        // Google Map (full screen)
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapLoaded = {
                isMapLoaded = true
            }
        ) {
            // Draw route polyline
            if (route.waypoints.size >= 2) {
                Polyline(
                    points = route.waypoints,
                    color = MaterialTheme.colorScheme.primary,
                    width = 12f
                )
            }

            // Start marker
            if (route.waypoints.isNotEmpty()) {
                Marker(
                    state = MarkerState(position = route.waypoints.first()),
                    title = "Start",
                    snippet = route.startAddress
                )
            }

            // End marker
            if (route.waypoints.size > 1) {
                Marker(
                    state = MarkerState(position = route.waypoints.last()),
                    title = "Finish",
                    snippet = route.endAddress
                )
            }

            // Current position marker (rider)
            Marker(
                state = MarkerState(position = currentPosition),
                title = "You",
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_BLUE
                )
            )
        }

        // Route info overlay (top-left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Target pace guidance (top-right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TARGET",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$targetCadence",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (kotlin.math.abs(metrics.cadence - targetCadence) <= 5)
                                Color(0xFF4CAF50)
                            else if (metrics.cadence < targetCadence)
                                Color(0xFFF44336)
                            else
                                Color(0xFFFF9800)
                        )
                        Text("rpm", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$targetResistance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text("res%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Connection indicator (below route info if disconnected)
        if (!connectionState.isConnected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 100.dp),
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

        // Progress bar (above bottom metrics bar)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 100.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${String.format("%.1f", progressPercent)}% complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    val remaining = (route.distanceMeters - virtualDistanceTraveled).coerceAtLeast(0f)
                    val remainingDisplay = if (useMetricUnits) {
                        String.format("%.1f km left", remaining / 1000f)
                    } else {
                        String.format("%.1f mi left", remaining / 1609.34f)
                    }
                    Text(
                        text = remainingDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
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

/**
 * Calculate distance between two LatLng points in meters using Haversine formula
 */
private fun calculateDistance(start: LatLng, end: LatLng): Float {
    val R = 6371000.0 // Earth's radius in meters
    val lat1 = Math.toRadians(start.latitude)
    val lat2 = Math.toRadians(end.latitude)
    val deltaLat = Math.toRadians(end.latitude - start.latitude)
    val deltaLon = Math.toRadians(end.longitude - start.longitude)

    val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return (R * c).toFloat()
}

/**
 * Interpolate between two LatLng points
 */
private fun interpolatePosition(start: LatLng, end: LatLng, progress: Float): LatLng {
    val lat = start.latitude + (end.latitude - start.latitude) * progress
    val lng = start.longitude + (end.longitude - start.longitude) * progress
    return LatLng(lat, lng)
}
