package com.toiletseva.toiletseva.data

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import com.toiletseva.toiletseva.data.PlaceType

class PlacesService(private val context: Context) {
    private val placesClient: PlacesClient by lazy {
        if (!Places.isInitialized()) {
            Places.initialize(context, "YOUR_API_KEY_HERE")
        }
        Places.createClient(context)
    }

    private val apiKey = "YOUR_API_KEY"
    
    private val searchCache = mutableMapOf<String, List<ToiletLocation>>()
    private val cacheTimeout = 5 * 60 * 1000L 

    suspend fun findNearbyBathrooms(
        location: LatLng,
        radius: Int = 500 
    ): Result<List<ToiletLocation>> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "${location.latitude},${location.longitude},$radius"
            val cachedResult = searchCache[cacheKey]
            if (cachedResult != null) {
                println("✅ Using cached results for $cacheKey")
                return@withContext Result.success(cachedResult)
            }

            val allPlaces = mutableListOf<ToiletLocation>()
            
            val essentialPlaceTypes = listOf(
                "restaurant",      
                "gas_station",     
                "shopping_mall",   
                "hospital",        
                "hotel",          
                "convenience_store", 
                "department_store",   
                "supermarket",        
                "park",              
                "library",           
                "museum",            
                "airport",           
                "train_station",     
                "bus_station",       
                "shopping_center"    
            )
            
            val placeTypeDeferreds = coroutineScope {
                essentialPlaceTypes.map { placeType ->
                    async {
                        try {
                            val places = searchNearbyPlaces(location, radius, placeType) 
                            println("🔍 Found ${places.size} places for type: $placeType")
                            places
                        } catch (e: Exception) {
                            println("❌ Error searching for $placeType: ${e.message}")
                            emptyList<ToiletLocation>()
                        }
                    }
                }
            }
            val placeTypeResults = placeTypeDeferreds.awaitAll().flatten()
            allPlaces.addAll(placeTypeResults)
            
            val comprehensiveTextSearches = listOf(
                "public restrooms near me",
                "public bathroom near me",
                "public toilet near me",
                "restroom near me",
                "bathroom near me",
                "toilet near me",
                "gas stations with restrooms near me",
                "restaurants with restrooms near me",
                "shopping mall restrooms near me",
                "hospital restrooms near me",
                "hotel lobby restrooms near me",
                "convenience store restrooms near me",
                "supermarket restrooms near me",
                "department store restrooms near me"
            )
            
            val textSearchDeferreds = coroutineScope {
                comprehensiveTextSearches.map { search ->
                    async {
                        try {
                            val places = searchTextPlaces(location, radius, search) 
                            println("🚽 Found ${places.size} places for: $search")
                            places
                        } catch (e: Exception) {
                            println("❌ Error searching for $search: ${e.message}")
                            emptyList<ToiletLocation>()
                        }
                    }
                }
            }
            val textSearchResults = textSearchDeferreds.awaitAll().flatten()
            allPlaces.addAll(textSearchResults)
            
            val placesWithDistance = allPlaces
                .distinctBy { it.googlePlaceId }
                .filter { it.latitude != 0.0 && it.longitude != 0.0 }
                .filter { place ->
                    val name = place.name.lowercase()
                    val address = place.address.lowercase()
                    
                    val unwantedKeywords = listOf(
                        "bus stop", "bus station", "transit station", "subway station", "train station",
                        "intersection", "traffic light", "crossing", "cemetery", "grave", "memorial",
                        "parking meter", "atm", "vending machine"
                    )
                    
                    val bathroomKeywords = listOf(
                        "restroom", "bathroom", "toilet", "washroom", "lavatory", "wc",
                        "restaurant", "gas", "station", "mall", "hospital", "hotel",
                        "store", "market", "supermarket", "convenience", "department",
                        "public", "facility", "center", "plaza", "park"
                    )
                    
                    val hasBathroomKeywords = bathroomKeywords.any { keyword -> 
                        name.contains(keyword) || address.contains(keyword)
                    }
                    
                    val hasUnwantedKeywords = unwantedKeywords.any { keyword -> 
                        name.contains(keyword) || address.contains(keyword)
                    }
                    
                    hasBathroomKeywords || !hasUnwantedKeywords
                }
                .map { place ->
                    val distance = calculateDistance(
                        location.latitude, location.longitude,
                        place.latitude, place.longitude
                    )
                    place.copy(distanceFromUser = distance)
                }
                .filter { it.distanceFromUser <= radius } 
                .sortedWith(compareByDescending<ToiletLocation> { it.placeType == PlaceType.PUBLIC_TOILET }
                    .thenByDescending { it.rating }
                    .thenByDescending { it.reviewCount }
                    .thenBy { it.distanceFromUser})
                .take(30) 
            
            println("🎯 FINAL RESULTS: Found ${placesWithDistance.size} bathroom locations within ${radius}m radius")
            placesWithDistance.take(5).forEach { place ->
                println("📍 ${place.name} - ${formatDistance(place.distanceFromUser)} away")
            }
            
            searchCache[cacheKey] = placesWithDistance
            
            cleanCache()
            
            Result.success(placesWithDistance)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAllPagesNearby(location: LatLng, radius: Int, type: String): List<ToiletLocation> {
        val allResults = mutableListOf<ToiletLocation>()
        var pageToken: String? = null
        var first = true
        do {
            val url = StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?")
                .append("location=${location.latitude},${location.longitude}")
                .append("&radius=$radius")
                .append("&type=$type")
                .append("&key=$apiKey")
            if (!first && pageToken != null) url.append("&pagetoken=$pageToken")
            first = false
            val response = URL(url.toString()).readText()
            val json = JSONObject(response)
            val status = json.getString("status")
            if (status == "OK" || status == "ZERO_RESULTS") {
                val results = json.getJSONArray("results")
                for (i in 0 until results.length()) {
                    val place = results.getJSONObject(i)
                    val toiletLocation = parseGooglePlaceToToiletLocation(place)
                    allResults.add(toiletLocation)
                }
                pageToken = if (json.has("next_page_token")) json.getString("next_page_token") else null
                if (pageToken != null) kotlinx.coroutines.delay(2000) 
            } else {
                pageToken = null
            }
        } while (pageToken != null)
        return allResults
    }

    private fun parseGooglePlaceToToiletLocation(place: JSONObject): ToiletLocation {
        val id = place.optString("place_id", "")
        val name = place.optString("name", "Unknown")
        val address = place.optString("vicinity", place.optString("formatted_address", ""))
        val lat = place.optJSONObject("geometry")?.optJSONObject("location")?.optDouble("lat") ?: 0.0
        val lng = place.optJSONObject("geometry")?.optJSONObject("location")?.optDouble("lng") ?: 0.0
        val types = mutableListOf<String>()
        if (place.has("types")) {
            val typesArray = place.getJSONArray("types")
            for (j in 0 until typesArray.length()) {
                types.add(typesArray.getString(j))
            }
        }
        val mappedPlaceType = mapGoogleTypeToPlaceType(types)
        return ToiletLocation(
            id = id,
            name = name,
            address = address,
            latitude = lat,
            longitude = lng,
            placeType = mappedPlaceType,
            isFromGooglePlaces = true,
            googlePlaceId = id
        )
    }

    private suspend fun fetchAllPagesText(location: LatLng, radius: Int, query: String): List<ToiletLocation> {
        val allResults = mutableListOf<ToiletLocation>()
        var pageToken: String? = null
        var first = true
        do {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = StringBuilder("https://maps.googleapis.com/maps/api/place/textsearch/json?")
                .append("query=$encodedQuery")
                .append("&location=${location.latitude},${location.longitude}")
                .append("&radius=$radius")
                .append("&key=$apiKey")
            if (!first && pageToken != null) url.append("&pagetoken=$pageToken")
            first = false
            val response = URL(url.toString()).readText()
            val json = JSONObject(response)
            val status = json.getString("status")
            if (status == "OK" || status == "ZERO_RESULTS") {
                val results = json.getJSONArray("results")
                for (i in 0 until results.length()) {
                    val place = results.getJSONObject(i)
                    val toiletLocation = parseGooglePlaceToToiletLocation(place)
                    allResults.add(toiletLocation)
                }
                pageToken = if (json.has("next_page_token")) json.getString("next_page_token") else null
                if (pageToken != null) kotlinx.coroutines.delay(2000)
            } else {
                pageToken = null
            }
        } while (pageToken != null)
        return allResults
    }

    private fun cleanCache() {
        val currentTime = System.currentTimeMillis()
        searchCache.entries.removeIf { entry ->
            currentTime - entry.key.hashCode() > cacheTimeout
        }
    }

    private fun mapGoogleTypeToPlaceType(types: List<String>): PlaceType {
        return when {
            types.contains("gas_station") -> PlaceType.GAS_STATION
            types.contains("restaurant") || types.contains("cafe") -> PlaceType.RESTAURANT_CAFE
            types.contains("hotel") -> PlaceType.HOTEL
            types.contains("shopping_mall") || types.contains("department_store") || types.contains("supermarket") || types.contains("convenience_store") -> PlaceType.RESTAURANT_CAFE 
            types.contains("train_station") || types.contains("transit_station") || types.contains("bus_station") -> PlaceType.METRO_TRAIN
            types.contains("park") -> PlaceType.PUBLIC_TOILET 
            else -> PlaceType.PUBLIC_TOILET
        }
    }

    private suspend fun searchNearbyPlaces(location: LatLng, radius: Int, type: String): List<ToiletLocation> {
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=${location.latitude},${location.longitude}" +
                "&radius=$radius" +
                "&type=$type" +
                "&key=$apiKey"
        
        try {
            println("🌐 Making API request for type '$type': $url")
            val response = URL(url).readText()
            val json = JSONObject(response)
            
            val status = json.getString("status")
            println("📡 API Response status for '$type': $status")
            
            if (status == "OK") {
                val results = json.getJSONArray("results")
                val places = mutableListOf<ToiletLocation>()
                
                println("✅ Found ${results.length()} results for type '$type'")
                
                for (i in 0 until results.length()) {
                    val place = results.getJSONObject(i)
                    val geometry = place.getJSONObject("geometry")
                    val locationObj = geometry.getJSONObject("location")
                    
                    val placeName = place.getString("name")
                    val placeAddress = place.optString("vicinity", "")
                    
                    places.add(
                        ToiletLocation(
                            id = place.getString("place_id"),
                            name = placeName,
                            address = placeAddress,
                            latitude = locationObj.getDouble("lat"),
                            longitude = locationObj.getDouble("lng"),
                            isPublic = true,
                            isFree = true,
                            rating = place.optDouble("rating", 0.0),
                            reviewCount = place.optInt("user_ratings_total", 0),
                            isFromGooglePlaces = true,
                            googlePlaceId = place.getString("place_id"),
                            photos = emptyList()
                        )
                    )
                    
                    println("📍 Added: $placeName - $placeAddress")
                }
                
                return places
            } else {
                println("❌ API Error for type '$type': $status")
                if (json.has("error_message")) {
                    println("🔍 Error message: ${json.getString("error_message")}")
                }
                return emptyList()
            }
        } catch (e: Exception) {
            println("💥 Exception searching for type '$type': ${e.message}")
            return emptyList()
        }
    }

    private suspend fun searchTextPlaces(location: LatLng, radius: Int, query: String): List<ToiletLocation> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/place/textsearch/json?" +
                "query=$encodedQuery" +
                "&location=${location.latitude},${location.longitude}" +
                "&radius=$radius" +
                "&key=$apiKey"
        
        try {
            println("🌐 Making text search for query '$query': $url")
            val response = URL(url).readText()
            val json = JSONObject(response)
            
            val status = json.getString("status")
            println("📡 Text search response status for '$query': $status")
            
            if (status == "OK") {
                val results = json.getJSONArray("results")
                val places = mutableListOf<ToiletLocation>()
                
                println("✅ Found ${results.length()} results for query '$query'")
                
                for (i in 0 until results.length()) {
                    val place = results.getJSONObject(i)
                    val geometry = place.getJSONObject("geometry")
                    val locationObj = geometry.getJSONObject("location")
                    
                    val placeName = place.getString("name")
                    val placeAddress = place.optString("formatted_address", "")
                    
                    places.add(
                        ToiletLocation(
                            id = place.getString("place_id"),
                            name = placeName,
                            address = placeAddress,
                            latitude = locationObj.getDouble("lat"),
                            longitude = locationObj.getDouble("lng"),
                            isPublic = true,
                            isFree = true,
                            rating = place.optDouble("rating", 0.0),
                            reviewCount = place.optInt("user_ratings_total", 0),
                            isFromGooglePlaces = true,
                            googlePlaceId = place.getString("place_id"),
                            photos = emptyList()
                        )
                    )
                    
                    println("📍 Added from text search: $placeName - $placeAddress")
                }
                
                return places
            } else {
                println("❌ Text search error for query '$query': $status")
                if (json.has("error_message")) {
                    println("🔍 Error message: ${json.getString("error_message")}")
                }
                return emptyList()
            }
        } catch (e: Exception) {
            println("💥 Exception in text search for query '$query': ${e.message}")
            return emptyList()
        }
    }

    suspend fun getPlaceDetails(placeId: String): Result<Place?> {
        return try {
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 
