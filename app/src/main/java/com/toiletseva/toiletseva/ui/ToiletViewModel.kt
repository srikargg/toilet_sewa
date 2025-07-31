package com.toiletseva.toiletseva.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.toiletseva.toiletseva.data.*
import com.toiletseva.toiletseva.data.TransportationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToiletViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseService = FirebaseService()
    private val placesService = PlacesService(application)
    private val refugeRestroomsService = RefugeRestroomsService(application)
    private val directionsService = DirectionsService(application)

    // State for toilets from both Google Places and Firebase
    private val _toilets = MutableStateFlow<List<ToiletLocation>>(emptyList())
    val toilets: StateFlow<List<ToiletLocation>> = _toilets.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Current location
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    // Filter options
    private val _filterOptions = MutableStateFlow(FilterOptions())
    val filterOptions: StateFlow<FilterOptions> = _filterOptions.asStateFlow()

    // Search radius in meters (default 500 meters - reduced for faster results)
    private val _searchRadius = MutableStateFlow(1.0) // default 1 mile
    val searchRadius: StateFlow<Double> = _searchRadius.asStateFlow()

    // Cache for recent searches to avoid duplicate API calls
    private val searchCache = mutableMapOf<String, List<ToiletLocation>>()
    private val cacheTimeout = 3 * 60 * 1000L // 3 minutes
    
    // Real-time Firebase listener
    private var firebaseListener: kotlinx.coroutines.Job? = null

    // Update search radius (input is miles)
    fun updateSearchRadius(radiusInMiles: Double) {
        _searchRadius.value = radiusInMiles
        _filterOptions.value = _filterOptions.value.copy(maxDistance = radiusInMiles)

        // Reload toilets with new radius if we have a location
        _currentLocation.value?.let { location ->
            loadToiletsNearby(location)
        }

        println("🔄 Search radius updated: ${radiusInMiles}mi, Filter distance: ${radiusInMiles}mi")
    }

    // Load toilets from both Google Places and Firebase
    fun loadToiletsNearby(location: LatLng) {
        viewModelScope.launch {
            // Stop previous real-time listener
            firebaseListener?.cancel()
            
            _isLoading.value = true
            _error.value = null
            _currentLocation.value = location

            try {
                val currentRadiusMiles = _searchRadius.value
                val currentRadiusMeters = (currentRadiusMiles * 1609.34).toInt()
                
                // Load from Google Places API for rating and distance data
                val placesResult = withContext(Dispatchers.IO) {
                    placesService.findNearbyBathrooms(location, currentRadiusMeters)
                }
                val placesToilets = if (placesResult.isSuccess) {
                    placesResult.getOrNull() ?: emptyList()
                } else {
                    println("⚠️ Places API issue: ${placesResult.exceptionOrNull()?.message}")
                    emptyList()
                }

                // Load from Refuge Restrooms API for detailed amenity filters
                val refugeResult = withContext(Dispatchers.IO) {
                    refugeRestroomsService.findNearbyBathrooms(location, currentRadiusMeters)
                }
                val refugeToilets = if (refugeResult.isSuccess) {
                    refugeResult.getOrNull() ?: emptyList()
                } else {
                    println("⚠️ Refuge Restrooms load issue: ${refugeResult.exceptionOrNull()?.message}")
                    emptyList()
                }

                // Start real-time Firebase listener
                firebaseListener = viewModelScope.launch {
                    firebaseService.getToiletsNearbyRealtime(
                        location.latitude,
                        location.longitude,
                        (currentRadiusMiles * 1609.34).toInt()
                    ).collectLatest { firebaseToilets ->
                        // Combine data sources: Google Places (rating/distance) + Refuge Restrooms (amenities) + Firebase (real-time)
                        val combinedToilets = combineDataSources(placesToilets, refugeToilets, firebaseToilets)
                        val filteredToilets = applyFilters(combinedToilets)
                        
                        _toilets.value = filteredToilets
                        println("🔄 Real-time update: ${filteredToilets.size} toilets available")
                    }
                }
                
            } catch (e: Exception) {
                _error.value = "Failed to load nearby toilets: ${e.message}"
                println("❌ Error loading toilets: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun cleanCache() {
        val currentTime = System.currentTimeMillis()
        searchCache.entries.removeIf { entry ->
            // Remove entries older than cacheTimeout
            currentTime - entry.key.hashCode() > cacheTimeout
        }
    }

    // Add a new toilet (user-submitted)
    fun addToilet(toilet: ToiletLocation) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = firebaseService.addToilet(toilet)
                if (result.isSuccess) {
                    _error.value = "Toilet submitted successfully! It will appear on the map shortly."
                    // Real-time listener will automatically update the map
                } else {
                    _error.value = "Failed to add toilet: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error adding toilet: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Add a review for a toilet
    fun addReview(toiletId: String, review: ToiletReview) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = firebaseService.addReview(toiletId, review)
                if (result.isSuccess) {
                    // Clear cache to force reload
                    searchCache.clear()
                    // Reload toilets to update ratings
                    _currentLocation.value?.let { location ->
                        loadToiletsNearby(location)
                    }
                } else {
                    _error.value = "Failed to add review: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error adding review: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Update filter options
    fun updateFilters(newFilters: FilterOptions) {
        // Clamp to max 10 miles
        val clampedDistance = newFilters.maxDistance.coerceIn(0.5, 10.0)
        _filterOptions.value = newFilters.copy(maxDistance = clampedDistance)

        // Always sync the search radius with the filter distance (miles)
        _searchRadius.value = clampedDistance

        // Convert to meters for backend/map
        val metersPerMile = 1609.34
        val newRadius = (clampedDistance * metersPerMile).toInt()
        _searchRadius.value = clampedDistance

        // Re-apply filters to current toilets (fast local filtering)
        val currentToilets = _toilets.value
        val filteredToilets = applyFilters(currentToilets)
        _toilets.value = filteredToilets

        println("🔄 Filters updated - Search radius: ${newRadius}m, Filter distance: ${clampedDistance}mi")
    }

    // Apply filters to toilet list
    private fun applyFilters(toilets: List<ToiletLocation>): List<ToiletLocation> {
        val filters = _filterOptions.value
        
        println("🔍 Applying filters: $filters")
        println("🔍 Total toilets before filtering: ${toilets.size}")
        
        // Check if we have any toilets with actual amenity data
        val hasAmenityData = toilets.any { toilet ->
            toilet.isGenderNeutral || toilet.isBabyFriendly || toilet.isDogFriendly || toilet.isWheelchairAccessible || !toilet.isFree
        }
        
        val refugeToilets = toilets.count { it.submittedBy == "Refuge Restrooms" }
        val googleToilets = toilets.count { it.isFromGooglePlaces }
        val firebaseToilets = toilets.count { it.submittedBy != "Refuge Restrooms" && !it.isFromGooglePlaces }
        
        println("🔍 Data sources: Refuge Restrooms ($refugeToilets), Google Places ($googleToilets), Firebase ($firebaseToilets)")
        println("🔍 Amenity data available: $hasAmenityData")
        
        // Log which filters are active and their data sources
        val activeFilters = mutableListOf<String>()
        if (filters.genderNeutralOnly) activeFilters.add("Gender Neutral (Refuge)")
        if (filters.babyFriendlyOnly) activeFilters.add("Baby Friendly (Refuge)")
        if (filters.wheelchairAccessibleOnly) activeFilters.add("Wheelchair Accessible (Refuge)")
        if (filters.freeOnly) activeFilters.add("Free Only (Refuge)")
        if (filters.approvedOnly) activeFilters.add("Approved Only (Refuge)")
        if (filters.minRating > 0) activeFilters.add("Rating ≥ ${filters.minRating} (All toilets)")
        if (filters.maxDistance < 20.0) activeFilters.add("Distance ≤ ${filters.maxDistance}km (All toilets)")
        
        if (activeFilters.isNotEmpty()) {
            println("🎯 Active filters: ${activeFilters.joinToString(", ")}")
        }
        
        if (hasAmenityData) {
            println("✅ Found toilets with detailed amenity information from Refuge Restrooms")
        } else {
            println("⚠️ No detailed amenity data available - amenity filters will not work")
        }
        
        val filteredToilets = mutableListOf<ToiletLocation>()
        val unfilteredToilets = mutableListOf<ToiletLocation>()
        
        toilets.forEach { toilet ->
            var passes = true
            var filterReason = ""
            
            // Amenity filters (from Refuge Restrooms API)
            if (filters.genderNeutralOnly && toilet.submittedBy == "Refuge Restrooms" && toilet.isGenderNeutral == false) {
                passes = false
                filterReason = "Not gender neutral"
            }
            
            if (filters.babyFriendlyOnly && toilet.submittedBy == "Refuge Restrooms" && toilet.isBabyFriendly == false) {
                passes = false
                filterReason = "Not baby friendly"
            }
            
            if (filters.dogFriendlyOnly && toilet.submittedBy == "Refuge Restrooms" && toilet.isDogFriendly == false) {
                passes = false
                filterReason = "Not dog friendly"
            }
            
            if (filters.wheelchairAccessibleOnly && toilet.submittedBy == "Refuge Restrooms" && toilet.isWheelchairAccessible == false) {
                passes = false
                filterReason = "Not wheelchair accessible"
            }
            
            if (filters.freeOnly && toilet.submittedBy == "Refuge Restrooms" && toilet.isFree == false) {
                passes = false
                filterReason = "Not free"
            }
            
            if (filters.approvedOnly && toilet.submittedBy == "Refuge Restrooms" && !toilet.isApproved) {
                passes = false
                filterReason = "Not approved"
            }
            
            // Rating filter (applies to all toilets with ratings)
            if (filters.minRating > 0) {
                // For user-submitted toilets, use their calculated rating
                // For API toilets, use their existing rating
                val toiletRating = if (toilet.submittedBy != "Refuge Restrooms" && !toilet.isFromGooglePlaces) {
                    // User-submitted toilet - use calculated rating from cleanliness and availability
                    toilet.rating
                } else {
                    // API toilet - use existing rating
                    toilet.rating
                }
                
                if (toiletRating > 0 && toiletRating < filters.minRating) {
                    passes = false
                    filterReason = "Rating ${toiletRating} < ${filters.minRating}"
                }
            }
            
            // Distance filter (applies to all toilets)
            if (filters.maxDistance < 20.0) {
                val maxDistanceMeters = filters.maxDistance * 1000
                if (toilet.distanceFromUser > maxDistanceMeters) {
                    passes = false
                    filterReason = "Distance ${toilet.distanceFromUser}m > ${maxDistanceMeters}m"
                }
            }
            
            if (passes) {
                filteredToilets.add(toilet)
            } else {
                println("🚫 Filtered out: ${toilet.name} (${toilet.submittedBy}) - $filterReason")
                unfilteredToilets.add(toilet.copy(isFiltered = true))
            }
        }
        
        println("🔍 Total toilets after filtering: ${filteredToilets.size}")
        println("🔍 Total toilets filtered out: ${unfilteredToilets.size}")
        
                // Only include filtered-out toilets if any filter (other than radius) is active
        val filtersActive = filters.genderNeutralOnly || filters.babyFriendlyOnly || filters.dogFriendlyOnly || filters.wheelchairAccessibleOnly || filters.freeOnly || filters.approvedOnly || filters.minRating > 0
        return if (filtersActive) {
            filteredToilets + unfilteredToilets
        } else {
            filteredToilets
        }
    }

    // Get place details for a specific toilet
    fun getPlaceDetails(placeId: String, onSuccess: (com.google.android.libraries.places.api.model.Place?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = placesService.getPlaceDetails(placeId)
                if (result.isSuccess) {
                    onSuccess(result.getOrNull())
                } else {
                    _error.value = "Failed to get place details: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error getting place details: ${e.message}"
            }
        }
    }

    // Test Firebase connection
    fun testFirebaseConnection() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Try to add a test toilet to verify Firebase is working
                val testToilet = ToiletLocation(
                    name = "Test Toilet",
                    address = "Test Address",
                    latitude = 20.5937,
                    longitude = 78.9629,
                    isPublic = true,
                    isFree = true,
                    submittedBy = "Test User"
                )

                val result = firebaseService.addToilet(testToilet)
                if (result.isSuccess) {
                    _error.value = "Firebase connection successful! Test toilet added."
                    // Remove the test toilet after a few seconds
                    kotlinx.coroutines.delay(3000)
                    _error.value = null
                } else {
                    _error.value = "Firebase connection failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Firebase test failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Combine data sources intelligently
    private fun combineDataSources(
        placesToilets: List<ToiletLocation>,
        refugeToilets: List<ToiletLocation>,
        firebaseToilets: List<ToiletLocation>
    ): List<ToiletLocation> {
        val combinedToilets = mutableListOf<ToiletLocation>()
        
        // Add Google Places toilets (for rating and distance)
        combinedToilets.addAll(placesToilets)
        
        // Add Firebase toilets
        combinedToilets.addAll(firebaseToilets)
        
        // Add Refuge Restrooms toilets, but enhance with Google Places data if available
        refugeToilets.forEach { refugeToilet ->
            // Try to find matching Google Places toilet by location proximity
            val nearbyPlacesToilet = placesToilets.find { placesToilet ->
                val distance = calculateDistance(
                    refugeToilet.latitude, refugeToilet.longitude,
                    placesToilet.latitude, placesToilet.longitude
                )
                distance < 50 // Within 50 meters
            }
            
            if (nearbyPlacesToilet != null) {
                // Enhance Refuge toilet with Google Places rating and distance data
                val enhancedToilet = refugeToilet.copy(
                    name = if (!nearbyPlacesToilet.name.isNullOrBlank()) nearbyPlacesToilet.name else refugeToilet.name,
                    placeType = nearbyPlacesToilet.placeType,
                    rating = nearbyPlacesToilet.rating,
                    reviewCount = nearbyPlacesToilet.reviewCount,
                    distanceFromUser = nearbyPlacesToilet.distanceFromUser,
                    googlePlaceId = nearbyPlacesToilet.googlePlaceId
                )
                combinedToilets.add(enhancedToilet)
                println("🔗 Enhanced Refuge toilet with Google Places data: ${refugeToilet.name}")
            } else {
                // Add Refuge toilet as-is
                combinedToilets.add(refugeToilet)
            }
        }
        
        // Remove duplicates and sort by distance
        return combinedToilets
            .distinctBy { it.googlePlaceId ?: it.id }
            .sortedBy { it.distanceFromUser }
    }

    // Clear error
    fun clearError() {
        _error.value = null
    }

    // Navigation state
    private val _currentNavigationRoute = MutableStateFlow<NavigationRoute?>(null)
    val currentNavigationRoute: StateFlow<NavigationRoute?> = _currentNavigationRoute.asStateFlow()

    private val _selectedTransportMode = MutableStateFlow(TransportationMode.DRIVING)
    val selectedTransportMode: StateFlow<TransportationMode> = _selectedTransportMode.asStateFlow()

    // Get navigation directions
    fun getNavigationDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TransportationMode = TransportationMode.DRIVING
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = directionsService.getDirections(origin, destination, mode)
                if (result.isSuccess) {
                    _currentNavigationRoute.value = result.getOrNull()
                } else {
                    _error.value = "Failed to get directions: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error getting directions: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Update transport mode
    fun updateTransportMode(mode: TransportationMode) {
        _selectedTransportMode.value = mode
        // Reload directions if we have a current route
        _currentNavigationRoute.value?.let { route ->
            getNavigationDirections(route.startLocation, route.endLocation, mode)
        }
    }
    
    // Clear navigation data to free memory
    fun clearNavigationData() {
        _currentNavigationRoute.value = null
        _toilets.value = emptyList()
        firebaseListener?.cancel() // Stop real-time listener
        searchCache.clear() // Clear cache when clearing data
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up resources when ViewModel is cleared
        clearNavigationData()
    }
} 