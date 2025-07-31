package com.toiletseva.toiletseva.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.toiletseva.toiletseva.data.ToiletLocation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToiletSubmissionScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToiletViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isFree by remember { mutableStateOf(true) }
    var isGenderNeutral by remember { mutableStateOf(false) }
    var isBabyFriendly by remember { mutableStateOf(false) }
    var isDogFriendly by remember { mutableStateOf(false) }
    var isWheelchairAccessible by remember { mutableStateOf(false) }
    var hasChangingTable by remember { mutableStateOf(false) }
    var hasPaper by remember { mutableStateOf(false) }
    var hasSoap by remember { mutableStateOf(false) }
    var hasHandDryer by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0.0) }
    var submittedBy by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showMapPicker by remember { mutableStateOf(false) }
    
    // Collect ViewModel state
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    
    // Auto-fill location if available
    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            latitude = location.latitude.toString()
            longitude = location.longitude.toString()
        }
    }
    
    // Handle submission success
    LaunchedEffect(error) {
        if (error != null) {
            if (error!!.contains("successful")) {
                showSuccessDialog = true
                viewModel.clearError()
            } else {
                errorMessage = error!!
                showErrorDialog = true
                viewModel.clearError()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Submit New Toilet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Basic Information Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Basic Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Toilet Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, contentDescription = null)
                            }
                        )
                        
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address *") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Home, contentDescription = null)
                            }
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = latitude,
                                onValueChange = { latitude = it },
                                label = { Text("Latitude *") },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                }
                            )
                            
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                label = { Text("Longitude *") },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                }
                            )
                            
                            // Use Current Location button
                            IconButton(
                                onClick = {
                                    currentLocation?.let { location ->
                                        latitude = location.latitude.toString()
                                        longitude = location.longitude.toString()
                                    }
                                },
                                enabled = currentLocation != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Use Current Location",
                                    tint = if (currentLocation != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }
                            
                            // Map Picker button
                            IconButton(
                                onClick = { showMapPicker = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Pick Location on Map",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        OutlinedTextField(
                            value = submittedBy,
                            onValueChange = { submittedBy = it },
                            label = { Text("Your Name (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            placeholder = {
                                Text("Anonymous")
                            }
                        )
                    }
                }
            }
            
            // Access & Cost Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Access & Cost",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isPublic,
                                onCheckedChange = { isPublic = it }
                            )
                            Text("Public Access")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isFree,
                                onCheckedChange = { isFree = it }
                            )
                            Text("Free to Use")
                        }
                    }
                }
            }
            
            // Amenities Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Amenities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isGenderNeutral,
                                onCheckedChange = { isGenderNeutral = it }
                            )
                            Text("Gender Neutral")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isBabyFriendly,
                                onCheckedChange = { isBabyFriendly = it }
                            )
                            Text("Baby Friendly")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isDogFriendly,
                                onCheckedChange = { isDogFriendly = it }
                            )
                            Text("Dog Friendly")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isWheelchairAccessible,
                                onCheckedChange = { isWheelchairAccessible = it }
                            )
                            Text("Wheelchair Accessible")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasChangingTable,
                                onCheckedChange = { hasChangingTable = it }
                            )
                            Text("Changing Table")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasPaper,
                                onCheckedChange = { hasPaper = it }
                            )
                            Text("Toilet Paper")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasSoap,
                                onCheckedChange = { hasSoap = it }
                            )
                            Text("Soap Available")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasHandDryer,
                                onCheckedChange = { hasHandDryer = it }
                            )
                            Text("Hand Dryer")
                        }
                    }
                }
            }
            
            // Rating Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Rating",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(5) { index ->
                                val starValue = index + 1
                                IconButton(
                                    onClick = { rating = starValue.toDouble() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rate $starValue stars",
                                        tint = if (rating >= starValue) {
                                            Color(0xFFFFD700) // Gold
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "Rating: ${if (rating > 0) "$rating/5" else "Not rated"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Helpful Tips Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Helpful Tips",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        
                        Text(
                            text = "• Use the location button to auto-fill your current coordinates\n" +
                                   "• Be as specific as possible with the address\n" +
                                   "• Include all available amenities to help others\n" +
                                   "• Your submission will appear on the map in real-time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // Submit Button
            item {
                Button(
                    onClick = {
                        if (name.isNotBlank() && address.isNotBlank() && 
                            latitude.isNotBlank() && longitude.isNotBlank()) {
                            
                            val lat = latitude.toDoubleOrNull()
                            val lng = longitude.toDoubleOrNull()
                            
                            if (lat != null && lng != null) {
                                val toilet = ToiletLocation(
                                    name = name,
                                    address = address,
                                    latitude = lat,
                                    longitude = lng,
                                    isPublic = isPublic,
                                    isFree = isFree,
                                    isGenderNeutral = isGenderNeutral,
                                    isBabyFriendly = isBabyFriendly,
                                    isDogFriendly = isDogFriendly,
                                    isWheelchairAccessible = isWheelchairAccessible,
                                    hasChangingTable = hasChangingTable,
                                    hasPaper = hasPaper,
                                    hasSoap = hasSoap,
                                    hasHandDryer = hasHandDryer,
                                    rating = rating,
                                    submittedBy = submittedBy.ifBlank { "Anonymous" }
                                )
                                
                                scope.launch {
                                    viewModel.addToilet(toilet)
                                }
                            } else {
                                errorMessage = "Invalid latitude or longitude values"
                                showErrorDialog = true
                            }
                        } else {
                            errorMessage = "Please fill in all required fields (Name, Address, Latitude, Longitude)"
                            showErrorDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && name.isNotBlank() && address.isNotBlank() &&
                             latitude.isNotBlank() && longitude.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Submit Toilet Location")
                }
            }
            
            // Spacer for bottom padding
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        // Success Dialog
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                title = {
                    Text("Success!")
                },
                text = {
                    Text("Your toilet location has been submitted successfully and will be available on the map shortly.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSuccessDialog = false
                            onBackPressed()
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Error Dialog
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = {
                    Text("Error")
                },
                text = {
                    Text(errorMessage)
                },
                confirmButton = {
                    TextButton(
                        onClick = { showErrorDialog = false }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Map Picker Dialog
        if (showMapPicker) {
            MapPickerDialog(
                currentLocation = currentLocation,
                onLocationSelected = { lat, lng ->
                    latitude = lat.toString()
                    longitude = lng.toString()
                    showMapPicker = false
                },
                onDismiss = { showMapPicker = false }
            )
        }
    }
} 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerDialog(
    currentLocation: LatLng?,
    onLocationSelected: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLocation by remember { mutableStateOf(currentLocation) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Pick Location")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Clear instructions at the top
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Drag the map to position the red indicator, then click 'Select Location'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AndroidView(
                        factory = { context ->
                            MapView(context).apply {
                                onCreate(null)
                                mapView = this
                                getMapAsync { map ->
                                    googleMap = map
                                    isMapReady = true
                                    
                                    // Enable map controls
                                    map.uiSettings.apply {
                                        isZoomControlsEnabled = true
                                        isMyLocationButtonEnabled = true
                                        isCompassEnabled = true
                                        isMapToolbarEnabled = false
                                    }
                                    
                                    // Set initial camera position
                                    val initialLocation = currentLocation ?: LatLng(0.0, 0.0)
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 15f))
                                    selectedLocation = initialLocation
                                    
                                    // Handle camera movement to update selected location
                                    map.setOnCameraMoveListener {
                                        val center = map.cameraPosition.target
                                        selectedLocation = center
                                    }
                                    
                                    // Handle when camera stops moving (final position)
                                    map.setOnCameraIdleListener {
                                        val center = map.cameraPosition.target
                                        selectedLocation = center
                                    }
                                    
                                    // Handle map clicks (optional - for direct selection)
                                    map.setOnMapClickListener { latLng ->
                                        selectedLocation = latLng
                                        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Center indicator (always visible)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Selected Location",
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Coordinate display
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedLocation?.let { location ->
                                "${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
                            } ?: "Loading coordinates...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                        selectedLocation?.let { location ->
                            onLocationSelected(location.latitude, location.longitude)
                        }
                    },
                    enabled = selectedLocation != null
                ) {
                    Text("Select Location")
                }
            }
        }
    )
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }
} 