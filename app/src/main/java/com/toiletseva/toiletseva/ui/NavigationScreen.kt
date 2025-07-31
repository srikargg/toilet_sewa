package com.toiletseva.toiletseva.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.PolylineOptions
import com.toiletseva.toiletseva.data.*
import com.toiletseva.toiletseva.data.formatDistance
import com.toiletseva.toiletseva.data.PolylineDecoder
import com.toiletseva.toiletseva.R
import kotlinx.coroutines.launch

// Function to calculate distance between two LatLng points
private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        point1.latitude, point1.longitude,
        point2.latitude, point2.longitude,
        results
    )
    return results[0]
}

// Function to start real-time location tracking
private fun startLocationTracking(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdate: (LatLng) -> Unit,
    onTrackingStateChange: (Boolean) -> Unit
) {
    // Create location request for high frequency updates
    val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        3000 // Update every 3 seconds for navigation
    ).setMinUpdateDistanceMeters(5f) // Update if moved more than 5 meters
     .build()

    // Request location updates
    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    onLocationUpdate(latLng)
                    println("📍 Navigation location update: ${location.latitude}, ${location.longitude}")
                }
            }
        },
        null
    ).addOnFailureListener { exception ->
        println("❌ Location tracking failed: ${exception.message}")
        onTrackingStateChange(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    toilet: ToiletLocation,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToiletViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var currentRoute by remember { mutableStateOf<NavigationRoute?>(null) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var selectedTransportMode by remember { mutableStateOf(TransportationMode.WALKING) }
    var showTransportModeSelector by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var polylineAdded by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLocationTracking by remember { mutableStateOf(false) }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val directionsService = remember { DirectionsService(context) }
    val viewModelUserLocation = viewModel.currentLocation.collectAsState().value

    // Permission launcher for location access
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (hasLocationPermission) {
            // Start location tracking when permission is granted
            startLocationTracking(
                fusedLocationClient,
                onLocationUpdate = { location ->
                    userLocation = location
                },
                onTrackingStateChange = { isTracking ->
                    isLocationTracking = isTracking
                }
            )
        }
    }

    // Check location permissions on mount
    LaunchedEffect(Unit) {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        hasLocationPermission = fineLocation == PackageManager.PERMISSION_GRANTED ||
                coarseLocation == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Start location tracking immediately if permission already granted
            startLocationTracking(
                fusedLocationClient,
                onLocationUpdate = { location ->
                    userLocation = location
                },
                onTrackingStateChange = { isTracking ->
                    isLocationTracking = isTracking
                }
            )
        }
    }

    // Get directions when component mounts or transport mode changes
    LaunchedEffect(toilet, selectedTransportMode, userLocation) {
        if (userLocation != null) {
            isLoading = true
            error = null
            polylineAdded = false
            
            println("🗺️ Navigation: Starting directions to ${toilet.name}")
            println("🗺️ Navigation: Toilet type: ${toilet.submittedBy}")
            println("🗺️ Navigation: Destination: ${toilet.latitude}, ${toilet.longitude}")
            println("🗺️ Navigation: Origin: ${userLocation!!.latitude}, ${userLocation!!.longitude}")
            
            val result = directionsService.getDirections(
                origin = userLocation!!,
                destination = LatLng(toilet.latitude, toilet.longitude),
                mode = selectedTransportMode
            )
            
            if (result.isSuccess) {
                currentRoute = result.getOrNull()
                currentStepIndex = 0
                
                // Decode polyline for route display
                currentRoute?.let { route ->
                    try {
                        routePolyline = PolylineDecoder.decode(route.polyline)
                        println("🗺️ Route polyline decoded: ${routePolyline.size} points")
                        println("🗺️ Route from: ${route.startLocation} to: ${route.endLocation}")
                    } catch (e: Exception) {
                        println("❌ Error decoding polyline: ${e.message}")
                        routePolyline = emptyList()
                    }
                }
            } else {
                error = result.exceptionOrNull()?.message ?: "Failed to get directions"
            }
            isLoading = false
        }
    }

    // Update map when polyline changes
    LaunchedEffect(routePolyline, googleMap) {
        if (routePolyline.isNotEmpty() && googleMap != null && !polylineAdded) {
            try {
                // Clear existing polylines
                googleMap?.clear()
                
                // Add destination marker
                val destinationMarker = MarkerOptions()
                    .position(LatLng(toilet.latitude, toilet.longitude))
                    .title(toilet.name)
                    .snippet("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                
                googleMap?.addMarker(destinationMarker)
                
                // Add route polyline
                val polylineOptions = PolylineOptions()
                    .addAll(routePolyline)
                    .color(android.graphics.Color.BLUE)
                    .width(8f)
                    .geodesic(true)
                
                googleMap?.addPolyline(polylineOptions)
                polylineAdded = true
                
                println("🗺️ Route polyline added to map with ${routePolyline.size} points")
                
                // Fit camera to show entire route
                if (routePolyline.isNotEmpty()) {
                    val builder = LatLngBounds.Builder()
                    routePolyline.forEach { latLng ->
                        builder.include(latLng)
                    }
                    // Also include start and end points
                    builder.include(LatLng(toilet.latitude, toilet.longitude))
                    userLocation?.let { builder.include(it) }
                    
                    val bounds = builder.build()
                    val padding = 100 // padding in pixels
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                    googleMap?.animateCamera(cameraUpdate)
                    
                    println("🗺️ Camera positioned to show entire route")
                }
            } catch (e: Exception) {
                println("❌ Error adding polyline to map: ${e.message}")
            }
        }
    }

    // Store context from AndroidView for use in LaunchedEffect
    var mapContext: android.content.Context? by remember { mutableStateOf(null) }

    // Update camera to follow user location when moving
    LaunchedEffect(userLocation, googleMap, mapContext) {
        if (userLocation != null && googleMap != null && !isLoading && mapContext != null) {
            // Clear all markers and polylines
            googleMap?.clear()
            // Add blue circle around user location (same as MapScreen)
            val circleOptions = CircleOptions()
                .center(userLocation!!)
                .radius(15.0) // 15 meters radius for user indicator
                .fillColor(android.graphics.Color.argb(30, 0, 150, 255)) // Semi-transparent blue
                .strokeColor(android.graphics.Color.argb(150, 0, 150, 255)) // Blue border
                .strokeWidth(2f)
            googleMap?.addCircle(circleOptions)
            // Add blue user location marker (same as MapScreen)
            val bitmapDescriptor = bitmapDescriptorFromVector(mapContext!!, R.drawable.ic_user_location_indicator)
            val markerOptions = MarkerOptions()
                .position(userLocation!!)
                .icon(bitmapDescriptor)
                .anchor(0.5f, 0.5f)
                .zIndex(1000f)
            googleMap?.addMarker(markerOptions)
            // Smoothly animate camera to user's current position
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(userLocation!!))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Google Maps
        AndroidView(
            factory = { context ->
                mapContext = context // Save context for marker rendering
                MapView(context).apply {
                    onCreate(null)
                    mapView = this
                    getMapAsync { map ->
                        googleMap = map
                        
                        // Enable map controls
                        map.uiSettings.apply {
                            isZoomControlsEnabled = false // We'll add custom zoom controls
                            isMyLocationButtonEnabled = true // Enable the built-in My Location button
                            isCompassEnabled = true
                            isMapToolbarEnabled = false
                        }
                        
                        // Enable My Location layer if permission granted
                        if (hasLocationPermission) {
                            try {
                                map.isMyLocationEnabled = true
                                println("✅ My Location enabled successfully in navigation")
                                // (Marker logic now handled in LaunchedEffect above, for consistency with MapScreen)
                                // Set up My Location button click listener
                                map.setOnMyLocationButtonClickListener {
                                    println("📍 My Location button clicked in navigation")
                                    // Return false to let Google Maps handle the default behavior
                                    false
                                }

                                // Set up My Location click listener
                                map.setOnMyLocationClickListener { location ->
                                    println("📍 User clicked on blue dot in navigation: ${location.latitude}, ${location.longitude}")
                                    userLocation = LatLng(location.latitude, location.longitude)
                                }
                                
                                // Set initial camera position
                                userLocation?.let { location ->
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 18f)) // Higher zoom for navigation
                                }
                            } catch (e: Exception) {
                                println("❌ Error enabling My Location in navigation: ${e.message}")
                            }
                        } else {
                            println("❌ Location permission not granted for navigation")
                        }
                        
                        // Add route polyline if available
                        if (routePolyline.isNotEmpty() && !polylineAdded) {
                            try {
                                // Clear existing polylines
                                map.clear()
                                
                                // Add destination marker
                                val destinationMarker = MarkerOptions()
                                    .position(LatLng(toilet.latitude, toilet.longitude))
                                    .title(toilet.name)
                                    .snippet("Destination")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                
                                map.addMarker(destinationMarker)
                                
                                // Add route polyline
                                val polylineOptions = PolylineOptions()
                                    .addAll(routePolyline)
                                    .color(android.graphics.Color.BLUE)
                                    .width(8f)
                                    .geodesic(true)
                                
                                map.addPolyline(polylineOptions)
                                polylineAdded = true
                                
                                println("🗺️ Route polyline added to navigation map with ${routePolyline.size} points")
                                
                                // Fit camera to show entire route
                                if (routePolyline.isNotEmpty()) {
                                    val builder = LatLngBounds.Builder()
                                    routePolyline.forEach { latLng ->
                                        builder.include(latLng)
                                    }
                                    // Also include start and end points
                                    builder.include(LatLng(toilet.latitude, toilet.longitude))
                                    userLocation?.let { builder.include(it) }
                                    
                                    val bounds = builder.build()
                                    val padding = 100 // padding in pixels
                                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                                    map.animateCamera(cameraUpdate)
                                    
                                    println("🗺️ Camera positioned to show entire route in navigation")
                                }
                            } catch (e: Exception) {
                                println("❌ Error adding polyline to navigation map: ${e.message}")
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Small Top Banner with Back, Center Map, Info, and Transport Mode Buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 8.dp, end = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button (far left)
                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Center Map Button (left, after back)
                    IconButton(
                        onClick = {
                            userLocation?.let { location ->
                                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 18f))
                            }
                            fusedLocationClient.getCurrentLocation(
                                com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                null
                            ).addOnSuccessListener { location ->
                                if (location != null) {
                                    userLocation = LatLng(location.latitude, location.longitude)
                                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation!!, 18f))
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Center Map",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Banner Title or leave blank for max map area
                    Text(
                        text = "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // Info Button (right)
                    IconButton(
                        onClick = { /* TODO: Show info dialog or about screen */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Transport Mode Button (rightmost)
                    IconButton(
                        onClick = { showTransportModeSelector = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Transport mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Zoom controls - below banner, bottom left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 200.dp, top = 64.dp), // Space below banner
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zoom in button
                FloatingActionButton(
                    onClick = {
                        googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom in"
                    )
                }
                
                // Zoom out button
                FloatingActionButton(
                    onClick = {
                        googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    Text(
                        text = "−",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Re-center button
                FloatingActionButton(
                    onClick = {
                        userLocation?.let { location ->
                            // Use proper Google Maps camera positioning
                            // This will center the user's location in the visible map area
                            googleMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(location, 18f)
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Re-center to my location"
                    )
                }
            }
        }

        // Current instruction banner (Google Maps style)
        currentRoute?.let { route ->
            if (route.steps.isNotEmpty() && currentStepIndex < route.steps.size) {
                val currentStep = route.steps[currentStepIndex]
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getManeuverIcon(currentStep.maneuver),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text(
                                        text = currentStep.instruction,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    
                                    Text(
                                        text = "${currentStep.distance} • ${currentStep.duration}",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                // Next step preview
                                if (currentStepIndex + 1 < route.steps.size) {
                                    val nextStep = route.steps[currentStepIndex + 1]
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.White.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Then",
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                            
                                            Spacer(modifier = Modifier.width(4.dp))
                                            
                                            Icon(
                                                imageVector = getManeuverIcon(nextStep.maneuver),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom information panel
        currentRoute?.let { route ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speedometer (only show for driving/bicycling)
                        if (selectedTransportMode != TransportationMode.WALKING) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "25",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "mph",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        // ETA and distance
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = route.duration,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${route.distance} • ${toilet.name}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                        
                        // Navigation controls
                        Row {
                            IconButton(
                                onClick = { 
                                    if (currentStepIndex > 0) {
                                        currentStepIndex--
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Previous step"
                                )
                            }
                            
                            IconButton(
                                onClick = { 
                                    if (currentStepIndex < route.steps.size - 1) {
                                        currentStepIndex++
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Next step"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Getting directions...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Error message
        error?.let { errorMessage ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBackPressed
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }

        // Transport mode selector dialog
        if (showTransportModeSelector) {
            TransportModeSelectorDialog(
                currentMode = selectedTransportMode,
                onModeSelected = { mode ->
                    selectedTransportMode = mode
                    showTransportModeSelector = false
                },
                onDismiss = { showTransportModeSelector = false }
            )
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            try {
                // Stop location tracking
                if (isLocationTracking) {
                    fusedLocationClient.removeLocationUpdates(
                        object : com.google.android.gms.location.LocationCallback() {
                            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                                // Empty implementation for cleanup
                            }
                        }
                    )
                    isLocationTracking = false
                }
                
                mapView?.onDestroy()
                mapView = null
                googleMap = null
            } catch (e: Exception) {
                println("Error cleaning up navigation map: ${e.message}")
            }
        }
    }
}

@Composable
fun TransportModeSelectorDialog(
    currentMode: TransportationMode,
    onModeSelected: (TransportationMode) -> Unit,
    onDismiss: () -> Unit
) {
    val transportModes = listOf(
        TransportationMode.DRIVING to "Driving",
        TransportationMode.WALKING to "Walking",
        TransportationMode.BICYCLING to "Bicycling"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Transport Mode")
        },
        text = {
            LazyColumn {
                items(transportModes) { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getManeuverIcon(maneuver: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (maneuver.lowercase()) {
        "turn-left" -> Icons.Default.ArrowBack
        "turn-right" -> Icons.Default.ArrowForward
        "straight" -> Icons.Default.ArrowForward
        "u-turn" -> Icons.Default.Refresh
        else -> Icons.Default.ArrowForward
    }
} 