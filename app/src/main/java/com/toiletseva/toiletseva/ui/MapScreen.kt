package com.toiletseva.toiletseva.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.CircleOptions
import com.toiletseva.toiletseva.data.ToiletLocation
import com.toiletseva.toiletseva.data.formatDistance
import com.toiletseva.toiletseva.data.PolylineDecoder
import com.toiletseva.toiletseva.data.DirectionsService
import com.toiletseva.toiletseva.data.TransportationMode
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.toiletseva.toiletseva.ui.bitmapDescriptorFromVector
import com.toiletseva.toiletseva.R

// Function to start location tracking
private fun startLocationTracking(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdate: (LatLng) -> Unit,
    onTrackingStateChange: (Boolean) -> Unit
) {
    // Create location request for high accuracy
    val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        10000 // 10 seconds
    ).setMinUpdateDistanceMeters(10f) // Update if moved more than 10 meters
     .build()

    // Request location updates
    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    onLocationUpdate(latLng)
                    println("📍 Map location update: ${location.latitude}, ${location.longitude}")
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
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: ToiletViewModel = viewModel()
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var selectedToilet by remember { mutableStateOf<ToiletLocation?>(null) }
    var showNavigation by remember { mutableStateOf(false) }
    var showRadiusSelector by remember { mutableStateOf(false) }
    var hasRequestedLocation by remember { mutableStateOf(false) } // Prevent multiple location requests
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var polylineAdded by remember { mutableStateOf(false) }
    var isLocationTracking by remember { mutableStateOf(false) }
    var showNewToiletNotification by remember { mutableStateOf(false) }
    var newToiletCount by remember { mutableStateOf(0) }


    // Collect ViewModel state
    val toilets by viewModel.toilets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchRadius by viewModel.searchRadius.collectAsState()
// Clamp for blue circle radius only, not needed as a top-level val

    val filterOptions by viewModel.filterOptions.collectAsState()
    
    // Track toilet count changes for notifications
    var previousToiletCount by remember { mutableStateOf(0) }
    LaunchedEffect(toilets.size) {
        if (toilets.size > previousToiletCount && previousToiletCount > 0) {
            newToiletCount = toilets.size - previousToiletCount
            showNewToiletNotification = true
            // Auto-hide notification after 3 seconds
            kotlinx.coroutines.delay(3000)
            showNewToiletNotification = false
        }
        previousToiletCount = toilets.size
    }

    // Show navigation screen if toilet is selected
    if (showNavigation && selectedToilet != null) {
        NavigationScreen(
            toilet = selectedToilet!!,
            onBackPressed = {
                showNavigation = false
                selectedToilet = null
                // Clear polyline when returning from navigation
                routePolyline = emptyList()
                polylineAdded = false
            },
            modifier = modifier
        )
        return
    }



    // Permission launcher
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

    // Check and request permissions - OPTIMIZED to run only once
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

    // Get user location when permission is granted - OPTIMIZED to run only once
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !hasRequestedLocation) {
            hasRequestedLocation = true
            
            // Try to get last known location first (fastest)
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                if (lastLocation != null) {
                    userLocation = LatLng(lastLocation.latitude, lastLocation.longitude)
                    viewModel.loadToiletsNearby(userLocation!!)
                    println("📍 Using last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                } else {
                    // Fallback to current location request
                    fusedLocationClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).addOnSuccessListener { location ->
                        if (location != null) {
                            userLocation = LatLng(location.latitude, location.longitude)
                            viewModel.loadToiletsNearby(userLocation!!)
                            println("📍 Got current location: ${location.latitude}, ${location.longitude}")
                        }
                    }
                }
            }
        }
    }

    // Update ViewModel with current location
    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            viewModel.loadToiletsNearby(location)
        }
    }

    // Move camera to user location when map is ready and location is available
    LaunchedEffect(isMapReady, userLocation, hasLocationPermission) {
        if (isMapReady && userLocation != null && hasLocationPermission) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f))
        }
    }

    // Update markers and radius circle when toilets or radius change
    LaunchedEffect(toilets, googleMap, searchRadius, userLocation) {
        googleMap?.let { map ->
            map.clear()
            // Draw blue circle for search radius around user location (clamped to 2 miles)
            userLocation?.let { location ->
                val metersPerMile = 1609.34
                val radiusMeters = (searchRadius * metersPerMile).coerceAtMost(3218.7)
                val circleOptions = CircleOptions()
                    .center(location)
                    .radius(radiusMeters)
                    .fillColor(android.graphics.Color.argb(30, 0, 150, 255)) // Semi-transparent blue
                    .strokeColor(android.graphics.Color.argb(150, 0, 150, 255)) // Blue border
                    .strokeWidth(2f)
                map.addCircle(circleOptions)

                // Add a fixed-size custom marker for user location
                val bitmapDescriptor = bitmapDescriptorFromVector(context, R.drawable.ic_user_location_indicator)
                val markerOptions = MarkerOptions()
                    .position(location)
                    .icon(bitmapDescriptor)
                    .anchor(0.5f, 0.5f)
                    .zIndex(1000f)
                map.addMarker(markerOptions)
            }
            // Add markers for each toilet
            toilets.forEach { toilet ->
                val distanceText = formatDistance(toilet.distanceFromUser)
                val markerOptions = MarkerOptions()
                    .position(LatLng(toilet.latitude, toilet.longitude))
                    .title(toilet.name)
                    .snippet("${toilet.address} • $distanceText away")
                
                // Determine marker color based on type
                val markerColor = when {
                    toilet.isFiltered -> BitmapDescriptorFactory.HUE_YELLOW // Yellow for filtered out toilets
                    toilet.submittedBy != "Refuge Restrooms" && !toilet.isFromGooglePlaces -> BitmapDescriptorFactory.HUE_GREEN // Green for user-submitted toilets
                    else -> BitmapDescriptorFactory.HUE_BLUE // Blue for toilets that pass filters
                }
                
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(markerColor))

                val marker = map.addMarker(markerOptions)
                marker?.tag = toilet
            }
            
            // Add route polyline if available
            if (routePolyline.isNotEmpty() && !polylineAdded) {
                try {
                    val polylineOptions = PolylineOptions()
                        .addAll(routePolyline)
                        .color(android.graphics.Color.BLUE)
                        .width(8f)
                        .geodesic(true)
                    
                    map.addPolyline(polylineOptions)
                    polylineAdded = true
                    println("🗺️ Route polyline added to main map")
                } catch (e: Exception) {
                    println("❌ Error adding polyline to main map: ${e.message}")
                }
            }
        }
    }

    // Get route polyline when toilet is selected
    LaunchedEffect(selectedToilet, userLocation) {
        if (selectedToilet != null && userLocation != null && !showNavigation) {
            val directionsService = DirectionsService(context)
            val result = directionsService.getDirections(
                origin = userLocation!!,
                destination = LatLng(selectedToilet!!.latitude, selectedToilet!!.longitude),
                mode = TransportationMode.DRIVING
            )
            
            if (result.isSuccess) {
                val route = result.getOrNull()
                if (route != null) {
                    try {
                        routePolyline = PolylineDecoder.decode(route.polyline)
                        polylineAdded = false // Reset to allow re-adding
                        println("🗺️ Route polyline decoded for main map: ${routePolyline.size} points")
                    } catch (e: Exception) {
                        println("❌ Error decoding polyline for main map: ${e.message}")
                        routePolyline = emptyList()
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Google Maps
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    onCreate(null)
                    mapView = this
                    getMapAsync { map ->
                        googleMap = map
                        isMapReady = true

                        // Enable map controls with My Location button
                        map.uiSettings.apply {
                            isZoomControlsEnabled = true
                            isMyLocationButtonEnabled = true
                            isCompassEnabled = true
                            isMapToolbarEnabled = true
                        }

                        // Move zoom controls to middle-right by adding bottom padding
                        // A pixel value is used here; this might need adjustment for different screen densities.
                        map.setPadding(0, 0, 0, 800)

                        // Enable My Location layer if permission granted
                        if (hasLocationPermission) {
                            try {
                                map.isMyLocationEnabled = true
                                println("✅ My Location enabled successfully")
                                
                                // Set up My Location button click listener
                                map.setOnMyLocationButtonClickListener {
                                    println("📍 My Location button clicked")
                                    // Return false to let Google Maps handle the default behavior
                                    false
                                }

                                // Set up My Location click listener
                                map.setOnMyLocationClickListener { location ->
                                    println("📍 User clicked on blue dot: ${location.latitude}, ${location.longitude}")
                                    userLocation = LatLng(location.latitude, location.longitude)
                                }
                                
                                // Set initial camera position
                                userLocation?.let { location ->
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                                }
                            } catch (e: Exception) {
                                println("❌ Error enabling My Location: ${e.message}")
                            }
                        } else {
                            println("❌ Location permission not granted")
                        }
                        
                        // Set up marker click listener
                        map.setOnMarkerClickListener { marker ->
                            val toilet = marker.tag as? ToiletLocation
                            if (toilet != null) {
                                selectedToilet = toilet
                                // Do NOT show navigation screen yet; show bottom popup instead
                                showNavigation = false
                                println("📍 Toilet selected: ${toilet.name}")
                            }
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Filter Status Indicator
        val hasActiveFilters = filterOptions.genderNeutralOnly || 
                             filterOptions.babyFriendlyOnly || 
                             filterOptions.dogFriendlyOnly || 
                             filterOptions.freeOnly || 
                             filterOptions.minRating > 0 || 
                             filterOptions.maxDistance < 20.0
        
        if (hasActiveFilters) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filters Active • ${String.format("%.1f", searchRadius / 1000.0)}km radius",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        


        // Location permission status
        if (!hasLocationPermission) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Location permission needed to show your position",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Show bottom popup with toilet info if marker is selected and not navigating
        if (selectedToilet != null && !showNavigation) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .wrapContentHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // Always show the original name from API or user, never a generic fallback
                            Text(
                                text = selectedToilet!!.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Show specific facility type for clarity
                            Text(
                                text = when (selectedToilet!!.placeType) {
                                    com.toiletseva.toiletseva.data.PlaceType.RESTAURANT_CAFE -> "Restaurant / Café"
                                    com.toiletseva.toiletseva.data.PlaceType.GAS_STATION -> "Gas Station"
                                    com.toiletseva.toiletseva.data.PlaceType.HOTEL -> "Hotel"
                                    com.toiletseva.toiletseva.data.PlaceType.METRO_TRAIN -> "Metro / Train Station"
                                    com.toiletseva.toiletseva.data.PlaceType.PUBLIC_TOILET -> "Public Toilet"
                                    else -> selectedToilet!!.placeType.displayName
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val rating = selectedToilet!!.rating
                                if (rating > 0f) {
                                    // Show stars (up to 5)
                                    repeat(5) { i ->
                                        val starFilled = rating >= i + 1
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            modifier = Modifier.size(16.dp).alpha(if (starFilled) 1f else 0.3f),
                                            contentDescription = null,
                                            tint = if (starFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", rating),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "No reviews yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = { showNavigation = true },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .defaultMinSize(minWidth = 48.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Navigate")
                        }
                    }
                }
            }
        }

        // Legend - below banner, top right
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Testing Legend",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Blue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Passes Filters",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Yellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Filtered Out",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "User Submitted",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Finding nearby bathrooms...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Error message
        error?.let { errorMessage ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearError() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Overlays: bathroom count, radius selector, legend
        @Composable
        fun Overlays() {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp)
            ) {
                // Left overlays (bathroom count + radius selector)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (toilets.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp, 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${toilets.size} bathrooms nearby (${formatRadius(searchRadius)})",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Radius: ${formatRadius(searchRadius)}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showRadiusSelector = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Change radius",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                // Legend overlay (right)
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Testing Legend", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                tint = Color.Blue,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Passes Filters", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                tint = Color.Yellow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Filtered Out", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                tint = Color.Green,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("User Submitted", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Overlays()

        // Small Top Banner with Centered Title and Info Button
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
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Center Map Button (left)
                    IconButton(
                        onClick = {
                            userLocation?.let { location ->
                                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user_location_indicator),
                            contentDescription = "Your Location",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // Centered Banner Title
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ToiletSEWA",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Info Button (right)
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Info Dialog Popup
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("How to Use ToiletSEWA", style = MaterialTheme.typography.headlineSmall) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "🗺️ Use the map to find bathrooms near you. You can set the search radius up to 2 miles and recenter the map anytime with the icon on the top left.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "📍 Tap a marker to see the restroom’s name, rating, and features. Click “directions” to navigate, and in the navigation screen you can change transport options in the top right.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "⚙️ The Filters tab lets you pick what you want to see, like ratings or accessibility features.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "➕ The Add tab lets you create a new restroom location. Choose the spot on the map, add details and a rating, and publish it for others to use.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showInfoDialog = false }) {
                        Text("Close")
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f)
            )
        }

        // Debug info for location status
// Add vertical spacing between overlays to reduce clutter
        if (hasLocationPermission && userLocation == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Getting your location...",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        // New toilet notification
        if (showNewToiletNotification) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$newToiletCount new toilet${if (newToiletCount > 1) "s" else ""} added!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    }
                }
            }
        }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (isLocationTracking) {
                    fusedLocationClient.removeLocationUpdates(
                        object : com.google.android.gms.location.LocationCallback() {}
                    )
                }
                mapView?.onDestroy()
                mapView = null
                googleMap = null
            } catch (e: Exception) {
                println("Error cleaning up map: ${e.message}")
            }
        }
    }
}

// Helper function to format radius for display (miles only)
fun formatRadius(radiusInMiles: Double): String {
    val meters = radiusInMiles * 1609.34
    return if (meters < 160) {
        "${meters.toInt()} m"
    } else {
        String.format("%.2f mi", radiusInMiles)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadiusSelectorDialog(
    currentRadiusMeters: Double,
    onRadiusSelected: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val minMeters = 10.0
    val maxMeters = 3218.7 // 2 miles in meters
    // Use a nonlinear scale: more granularity at low end, coarser at high end
    val sliderSteps = 100
    // Map slider value [0, sliderSteps] to meters
    fun sliderToMeters(slider: Float): Double {
        // Exponential scale for better UI feel
        val fraction = slider / sliderSteps
        return minMeters * Math.pow(maxMeters / minMeters, fraction.toDouble())
    }
    fun metersToSlider(meters: Double): Float {
        val fraction = Math.log(meters / minMeters) / Math.log(maxMeters / minMeters)
        return (fraction * sliderSteps).toFloat()
    }
    var sliderValue by remember { mutableStateOf(metersToSlider(currentRadiusMeters)) }
    val radiusMeters = sliderToMeters(sliderValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Search Radius")
        },
        text = {
            Column {
                Text(
                    text = "Maximum Distance",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val displayText = if (radiusMeters < 160) {
                    "${radiusMeters.toInt()} m"
                } else {
                    val miles = radiusMeters / 1609.34
                    String.format("%.2f mi", miles)
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..sliderSteps.toFloat(),
                    steps = sliderSteps - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0.01 mi", // ~16m
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2 mi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onRadiusSelected(radiusMeters)
                        onDismiss()
                    }
                ) {
                    Text("Apply")
                }
            }
        }
    )
}

private fun calculateDistance(location1: LatLng, location2: LatLng): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        location1.latitude, location1.longitude,
        location2.latitude, location2.longitude,
        results
    )
    return results[0]
}

 