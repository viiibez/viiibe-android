package com.viiibe.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viiibe.app.bluetooth.BluetoothViewModel
import com.viiibe.app.ui.viewmodel.UserViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    bluetoothViewModel: BluetoothViewModel,
    userViewModel: UserViewModel,
    onStartRoute: (VirtualRoute) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeUser by userViewModel.activeUser.collectAsState()

    var selectedPresetRoute by remember { mutableStateOf<VirtualRoute?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val useMetricUnits = activeUser?.useMetricUnits == true

    // Predefined routes with actual GPS waypoints
    val presetRoutes = remember {
        listOf(
            VirtualRoute(
                name = "Central Park Loop",
                startAddress = "Central Park South, NYC",
                endAddress = "Central Park South, NYC",
                distanceMeters = 9700f, // ~6 miles
                waypoints = listOf(
                    LatLng(40.7648, -73.9724), // Start - Central Park South
                    LatLng(40.7680, -73.9819), // Columbus Circle
                    LatLng(40.7829, -73.9654), // North end - Harlem Meer
                    LatLng(40.7969, -73.9490), // Northeast
                    LatLng(40.7967, -73.9522), // North loop
                    LatLng(40.7812, -73.9665), // Reservoir
                    LatLng(40.7736, -73.9712), // Great Lawn
                    LatLng(40.7648, -73.9724)  // Back to start
                ),
                elevationGain = 50
            ),
            VirtualRoute(
                name = "Golden Gate Bridge",
                startAddress = "Fort Point, San Francisco",
                endAddress = "Vista Point, Sausalito",
                distanceMeters = 4800f, // ~3 miles
                waypoints = listOf(
                    LatLng(37.8107, -122.4769), // Fort Point
                    LatLng(37.8155, -122.4783), // Bridge start
                    LatLng(37.8199, -122.4783), // South tower
                    LatLng(37.8270, -122.4796), // Mid-span
                    LatLng(37.8324, -122.4795), // North tower
                    LatLng(37.8324, -122.4713)  // Vista Point
                ),
                elevationGain = 70
            ),
            VirtualRoute(
                name = "Chicago Lakefront Trail",
                startAddress = "Navy Pier, Chicago",
                endAddress = "Montrose Beach, Chicago",
                distanceMeters = 8000f, // ~5 miles
                waypoints = listOf(
                    LatLng(41.8917, -87.6086), // Navy Pier
                    LatLng(41.8967, -87.6128), // Ohio Street Beach
                    LatLng(41.9108, -87.6264), // North Avenue Beach
                    LatLng(41.9250, -87.6389), // Diversey Harbor
                    LatLng(41.9420, -87.6480), // Belmont Harbor
                    LatLng(41.9636, -87.6465)  // Montrose Beach
                ),
                elevationGain = 10
            ),
            VirtualRoute(
                name = "Venice Beach Boardwalk",
                startAddress = "Santa Monica Pier, CA",
                endAddress = "Venice Pier, CA",
                distanceMeters = 4000f, // ~2.5 miles
                waypoints = listOf(
                    LatLng(34.0094, -118.4973), // Santa Monica Pier
                    LatLng(34.0031, -118.4900), // Ocean Park
                    LatLng(33.9938, -118.4798), // Venice Beach
                    LatLng(33.9850, -118.4720)  // Venice Pier
                ),
                elevationGain = 5
            ),
            VirtualRoute(
                name = "Brooklyn Bridge Loop",
                startAddress = "City Hall, Manhattan",
                endAddress = "City Hall, Manhattan",
                distanceMeters = 3200f, // ~2 miles
                waypoints = listOf(
                    LatLng(40.7128, -74.0060), // City Hall
                    LatLng(40.7061, -73.9969), // Brooklyn Bridge Manhattan side
                    LatLng(40.7017, -73.9899), // Mid-bridge
                    LatLng(40.6983, -73.9903), // Brooklyn side
                    LatLng(40.6944, -73.9905), // DUMBO
                    LatLng(40.7017, -73.9899), // Back over
                    LatLng(40.7128, -74.0060)  // City Hall
                ),
                elevationGain = 40
            ),
            VirtualRoute(
                name = "San Diego Bay",
                startAddress = "Harbor Island, San Diego",
                endAddress = "Coronado Ferry Landing",
                distanceMeters = 6400f, // ~4 miles
                waypoints = listOf(
                    LatLng(32.7227, -117.1932), // Harbor Island
                    LatLng(32.7157, -117.1611), // Downtown Embarcadero
                    LatLng(32.7048, -117.1620), // Seaport Village
                    LatLng(32.6987, -117.1698), // Convention Center
                    LatLng(32.6991, -117.1773)  // Coronado Ferry Landing
                ),
                elevationGain = 5
            )
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
            text = "Virtual Routes",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Ride famous routes from around the world",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Selected route info
        selectedPresetRoute?.let { route ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedPresetRoute = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    }

                    Text(
                        text = "${route.startAddress} → ${route.endAddress}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Distance
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Straighten,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val distance = if (useMetricUnits) {
                                String.format("%.1f km", route.distanceMeters / 1000f)
                            } else {
                                String.format("%.1f mi", route.distanceMeters / 1609.34f)
                            }
                            Text(
                                text = distance,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Distance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Estimated time
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val cyclingTimeMinutes = (route.distanceMeters / 1000f / 20f * 60).toInt()
                            val hours = cyclingTimeMinutes / 60
                            val minutes = cyclingTimeMinutes % 60
                            val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Est. Time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Elevation
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Terrain,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val elevation = if (useMetricUnits) {
                                "${route.elevationGain}m"
                            } else {
                                "${(route.elevationGain * 3.28084).toInt()}ft"
                            }
                            Text(
                                text = elevation,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Elevation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Calories
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val calories = (route.distanceMeters / 1609.34f * 40).toInt()
                            Text(
                                text = "$calories",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Calories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Start ride button
                    Button(
                        onClick = { onStartRoute(route) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Virtual Ride", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Routes list
        Text(
            text = if (selectedPresetRoute == null) "Select a Route" else "Other Routes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        presetRoutes.forEach { route ->
            val isSelected = selectedPresetRoute?.name == route.name
            Surface(
                onClick = { selectedPresetRoute = route },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Route,
                        contentDescription = null,
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        val distance = if (useMetricUnits) {
                            String.format("%.1f km", route.distanceMeters / 1000f)
                        } else {
                            String.format("%.1f mi", route.distanceMeters / 1609.34f)
                        }
                        Text(
                            text = "$distance • ${route.startAddress}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
