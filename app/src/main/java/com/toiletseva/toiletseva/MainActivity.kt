package com.toiletseva.toiletseva

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import com.toiletseva.toiletseva.ui.theme.ToiletSEWATheme
import com.toiletseva.toiletseva.ui.MapScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home

import androidx.lifecycle.viewmodel.compose.viewModel
import com.toiletseva.toiletseva.ui.ToiletViewModel
import com.toiletseva.toiletseva.data.FilterOptions
import com.toiletseva.toiletseva.data.ToiletLocation
import com.toiletseva.toiletseva.data.PlaceType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Reduced memory logging for better performance
        if (BuildConfig.DEBUG) {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.d("MainActivity", "Memory usage: ${usedMemory}MB")
        }
        
        setContent {
            ToiletSEWATheme {
                ToiletSEWAApp()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Only log memory in debug builds
        if (BuildConfig.DEBUG) {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.d("MainActivity", "Memory usage on resume: ${usedMemory}MB")
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Only log memory in debug builds
        if (BuildConfig.DEBUG) {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.d("MainActivity", "Memory usage on pause: ${usedMemory}MB")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToiletSEWAApp() {
    var selectedTab by remember { mutableStateOf(0) }
    var showWelcomeDialog by remember { mutableStateOf(true) }
    
    // Welcome Dialog
    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = { showWelcomeDialog = false },
            title = {
                Text("Welcome to ToiletSEWA! 🚽")
            },
            text = {
                Text("Hi there! This is a beta version of our global restroom locator. We’re constantly improving and adding new features. Use the app to find, add, and review restrooms, and help us make sanitation more accessible for everyone!")
            },
            confirmButton = {
                Button(
                    onClick = { showWelcomeDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(

        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                    label = { Text("Map") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                    label = { Text("Add") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Filters") },
                    label = { Text("Filters") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )

            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> MapScreen(modifier = Modifier.padding(paddingValues))
            1 -> AddToiletScreen(modifier = Modifier.padding(paddingValues))
            2 -> FiltersScreen(modifier = Modifier.padding(paddingValues))

                }
            }
}

@Composable
fun AddToiletScreen(modifier: Modifier = Modifier) {
    val viewModel: ToiletViewModel = viewModel()
    val scope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var selectedPlaceType by remember { mutableStateOf(PlaceType.PUBLIC_TOILET) }
    var isPublic by remember { mutableStateOf(false) }
    var isFree by remember { mutableStateOf(false) }
    var isGenderNeutral by remember { mutableStateOf(false) }
    var isBabyFriendly by remember { mutableStateOf(false) }
    var isDogFriendly by remember { mutableStateOf(false) }
    var isWheelchairAccessible by remember { mutableStateOf(false) }
    var hasChangingTable by remember { mutableStateOf(false) }
    var hasPaper by remember { mutableStateOf(false) }
    var hasSoap by remember { mutableStateOf(false) }
    var hasHandDryer by remember { mutableStateOf(false) }
    var hasRunningWater by remember { mutableStateOf(false) }
    var hasShower by remember { mutableStateOf(false) }
    var cleanlinessRating by remember { mutableStateOf(0.0) }
    var availabilityRating by remember { mutableStateOf(0.0) }
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
            if (latitude.isBlank() && longitude.isBlank()) {
                latitude = String.format("%.6f", location.latitude)
                longitude = String.format("%.6f", location.longitude)
            }
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
                Text(
                    text = "Add New Toilet",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
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
                            text = "Basic Details",
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
                            },
                            placeholder = {
                                Text("e.g., Public Toilet - Connaught Place")
                            }
                        )
                        
                        // Place Type Selection
                        var showPlaceTypeDialog by remember { mutableStateOf(false) }
                        
                        OutlinedTextField(
                            value = selectedPlaceType.displayName,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Type of Place *") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPlaceTypeDialog = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select type")
                                }
                            }
                        )
                        
                        // Place Type Dialog
                        if (showPlaceTypeDialog) {
                            AlertDialog(
                                onDismissRequest = { showPlaceTypeDialog = false },
                                title = { Text("Select Place Type") },
                                text = {
                                    LazyColumn {
                                        items(PlaceType.values()) { placeType ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        selectedPlaceType = placeType
                                                        showPlaceTypeDialog = false
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = selectedPlaceType == placeType,
                                                    onClick = { 
                                                        selectedPlaceType = placeType
                                                        showPlaceTypeDialog = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = placeType.displayName,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showPlaceTypeDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                        
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
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (latitude.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            )
                            
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                label = { Text("Longitude *") },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (longitude.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            )
                            
                            // Use Current Location button
                            IconButton(
                                onClick = {
                                    currentLocation?.let { location ->
                                        latitude = String.format("%.6f", location.latitude)
                                        longitude = String.format("%.6f", location.longitude)
                                        println("📍 Current location set: $latitude, $longitude")
                                    }
                                },
                                enabled = currentLocation != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = if (currentLocation != null) "Use Current Location" else "Location not available",
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
                        
                        // Show coordinate status
                        if (latitude.isNotBlank() && longitude.isNotBlank()) {
                            Text(
                                text = "✅ Coordinates set: $latitude, $longitude",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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
                            Text("🚹 Gender Neutral (Unisex)")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isBabyFriendly,
                                onCheckedChange = { isBabyFriendly = it }
                            )
                            Text("👶 Baby-friendly changing station")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isDogFriendly,
                                onCheckedChange = { isDogFriendly = it }
                            )
                            Text("🐶 Dog-friendly")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isWheelchairAccessible,
                                onCheckedChange = { isWheelchairAccessible = it }
                            )
                            Text("♿ Wheelchair accessible")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasRunningWater,
                                onCheckedChange = { hasRunningWater = it }
                            )
                            Text("💧 Running water available")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasShower,
                                onCheckedChange = { hasShower = it }
                            )
                            Text("🚿 Shower available (optional)")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasPaper,
                                onCheckedChange = { hasPaper = it }
                            )
                            Text("🧻 Toilet Paper")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasSoap,
                                onCheckedChange = { hasSoap = it }
                            )
                            Text("🧼 Soap Available")
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasHandDryer,
                                onCheckedChange = { hasHandDryer = it }
                            )
                            Text("💨 Hand Dryer")
                        }
                    }
                }
            }
            
            // Ratings Card
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
                            text = "Ratings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Cleanliness Rating
                        Text(
                            text = "Cleanliness",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(5) { index ->
                                val starValue = index + 1
                                IconButton(
                                    onClick = { cleanlinessRating = starValue.toDouble() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rate $starValue stars",
                                        tint = if (cleanlinessRating >= starValue) {
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
                            text = "Cleanliness: ${if (cleanlinessRating > 0) "$cleanlinessRating/5" else "Not rated"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Availability Rating
                        Text(
                            text = "Availability",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(5) { index ->
                                val starValue = index + 1
                                IconButton(
                                    onClick = { availabilityRating = starValue.toDouble() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rate $starValue stars",
                                        tint = if (availabilityRating >= starValue) {
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
                            text = "Availability: ${if (availabilityRating > 0) "$availabilityRating/5" else "Not rated"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    placeType = selectedPlaceType,
                                    isPublic = isPublic,
                                    isFree = isFree,
                                    isGenderNeutral = isGenderNeutral,
                                    isBabyFriendly = isBabyFriendly,
                                    isDogFriendly = isDogFriendly,
                                    isWheelchairAccessible = isWheelchairAccessible,
                                    hasChangingTable = isBabyFriendly, // Auto-set if baby-friendly
                                    hasPaper = hasPaper,
                                    hasSoap = hasSoap,
                                    hasHandDryer = hasHandDryer,
                                    hasRunningWater = hasRunningWater,
                                    hasShower = hasShower,
                                    cleanlinessRating = cleanlinessRating,
                                    availabilityRating = availabilityRating,
                                    rating = (cleanlinessRating + availabilityRating) / 2.0, // Average rating
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
                        onClick = { showSuccessDialog = false }
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
                    latitude = String.format("%.6f", lat)
                    longitude = String.format("%.6f", lng)
                    showMapPicker = false
                },
                onDismiss = { showMapPicker = false }
            )
        }
    }
}

@Composable
fun FiltersScreen(modifier: Modifier = Modifier) {
    val viewModel: ToiletViewModel = viewModel()
    val filterOptions by viewModel.filterOptions.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchRadius by viewModel.searchRadius.collectAsState()
    
    var localFilters by remember { mutableStateOf(filterOptions) }
    var showSuccessPopup by remember { mutableStateOf(false) }
    
    // Sync default distance with current search radius
    LaunchedEffect(searchRadius) {
        if (localFilters.maxDistance != searchRadius / 1000.0) {
            localFilters = localFilters.copy(maxDistance = searchRadius / 1000.0)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Filters",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Info card about filter limitations
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Hybrid approach: Google Places (rating/distance) + Refuge Restrooms (amenity filters).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gender Neutral Filter (Refuge Restrooms)
            item {
                FilterCard(
                    title = "Gender Neutral",
                    subtitle = "Show only gender-neutral bathrooms (Refuge Restrooms)",
                    icon = Icons.Default.Person,
                    isEnabled = localFilters.genderNeutralOnly,
                    onToggle = { localFilters = localFilters.copy(genderNeutralOnly = it) }
                )
            }
            
            // Baby Friendly Filter (Refuge Restrooms)
            item {
                FilterCard(
                    title = "Baby Friendly",
                    subtitle = "Show bathrooms with changing tables (Refuge Restrooms)",
                    icon = Icons.Default.Add,
                    isEnabled = localFilters.babyFriendlyOnly,
                    onToggle = { localFilters = localFilters.copy(babyFriendlyOnly = it) }
                )
            }
            
            // Dog Friendly Filter (Refuge Restrooms)
            item {
                FilterCard(
                    title = "Dog Friendly",
                    subtitle = "Show pet-friendly bathrooms (Refuge Restrooms)",
                    icon = Icons.Default.Info,
                    isEnabled = localFilters.dogFriendlyOnly,
                    onToggle = { localFilters = localFilters.copy(dogFriendlyOnly = it) }
                )
            }
            
            // Wheelchair Accessible Filter (Refuge Restrooms)
            item {
                FilterCard(
                    title = "Wheelchair Accessible",
                    subtitle = "Show accessible bathrooms (Refuge Restrooms)",
                    icon = Icons.Default.Person,
                    isEnabled = localFilters.wheelchairAccessibleOnly,
                    onToggle = { localFilters = localFilters.copy(wheelchairAccessibleOnly = it) }
                )
            }
            
            // Free Only Filter (Refuge Restrooms)
            item {
                FilterCard(
                    title = "Free Only",
                    subtitle = "Show only free bathrooms (Refuge Restrooms)",
                    icon = Icons.Default.Check,
                    isEnabled = localFilters.freeOnly,
                    onToggle = { localFilters = localFilters.copy(freeOnly = it) }
                )
            }
            
            // Approved Only Filter (Refuge Restrooms)
            item {
                FilterCard(
                    title = "Approved Only",
                    subtitle = "Show only approved bathrooms (Refuge Restrooms)",
                    icon = Icons.Default.Star,
                    isEnabled = localFilters.approvedOnly,
                    onToggle = { localFilters = localFilters.copy(approvedOnly = it) }
                )
            }
            
            // Rating Filter (Google Places)
            item {
                RatingFilterCard(
                    minRating = localFilters.minRating,
                    onRatingChanged = { localFilters = localFilters.copy(minRating = it) }
                )
            }
            
            
        }
        
        // Apply Button
        Button(
            onClick = {
                viewModel.updateFilters(localFilters)
                // Show confirmation after filters are processed
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(100) // Short delay to ensure filters are applied
                    showSuccessPopup = true
                    kotlinx.coroutines.delay(2000) // Show for 2 seconds
                    showSuccessPopup = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isLoading) "Applying Filters..." else "Apply Filters",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Clear All Filters Button
        OutlinedButton(
            onClick = {
                localFilters = FilterOptions() // Reset to default values
                viewModel.updateFilters(localFilters)
                // Show confirmation after filters are processed
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(100) // Short delay to ensure filters are applied
                    showSuccessPopup = true
                    kotlinx.coroutines.delay(2000) // Show for 2 seconds
                    showSuccessPopup = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            enabled = !isLoading
        ) {
            Text(
                text = "Clear All Filters",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
    
    // Success Popup
    if (showSuccessPopup) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50) // Green color
                ),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Filters Applied Successfully!",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FilterCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isEnabled) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isEnabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnabled) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
fun RatingFilterCard(
    minRating: Double,
    onRatingChanged: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Minimum Rating",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (minRating > 0) "${minRating}★ and above (Google Places)" else "Any rating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Star rating selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(5) { index ->
                    val rating = (index + 1).toDouble()
                    IconButton(
                        onClick = { 
                            onRatingChanged(if (minRating == rating) 0.0 else rating)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "${rating} stars",
                            tint = if (rating <= minRating) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DistanceFilterCard(
    maxDistance: Double,
    onDistanceChanged: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Maximum Distance",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${String.format("%.1f", maxDistance)} km (Google Places)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Slider(
                value = maxDistance.toFloat(),
                onValueChange = { onDistanceChanged(it.toDouble()) },
                valueRange = 0.5f..20.0f,
                steps = 39, // 0.5 to 20.0 with 0.5 steps
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
                    text = "0.5 km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "20 km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "User settings and reviews",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    var mapError by remember { mutableStateOf<String?>(null) }
    
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
                
                // Show map loading or error status
                if (!isMapReady && mapError == null) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading map...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                mapError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
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
                                    try {
                                        googleMap = map
                                        isMapReady = true
                                        mapError = null
                                        
                                        // Enable map controls
                                        map.uiSettings.apply {
                                            isZoomControlsEnabled = true
                                            isMyLocationButtonEnabled = true
                                            isCompassEnabled = true
                                            isMapToolbarEnabled = false
                                        }
                                        
                                        // Set initial camera position
                                        val initialLocation = currentLocation ?: LatLng(37.4219983, -122.084) // Default to a known location
                                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 15f))
                                        println("🗺️ Map initialized at: ${initialLocation.latitude}, ${initialLocation.longitude}")
                                        
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
                                    } catch (e: Exception) {
                                        mapError = "Map initialization failed: ${e.message}"
                                        println("❌ Map error: ${e.message}")
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
                            println("📍 Location selected from map: ${location.latitude}, ${location.longitude}")
                            onLocationSelected(location.latitude, location.longitude)
                        }
                    },
                    enabled = selectedLocation != null
                ) {
                    Text(if (selectedLocation != null) "Select Location" else "Select")
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