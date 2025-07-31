package com.toiletseva.toiletseva.data

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import android.location.Location

class RefugeRestroomsService(private val context: Context) {
    
    private val searchCache = mutableMapOf<String, List<ToiletLocation>>()
    private val cacheTimeout = 10 * 60 * 1000L 

    suspend fun findNearbyBathrooms(
        location: LatLng,
        radius: Int = 5000
    ): Result<List<ToiletLocation>> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "refuge_${location.latitude},${location.longitude},$radius"
            val cachedResult = searchCache[cacheKey]
            if (cachedResult != null) {
                println("✅ Using cached Refuge Restrooms results for $cacheKey")
                return@withContext Result.success(cachedResult)
            }

            val url = "https://www.refugerestrooms.org/api/v1/restrooms/by_location?" +
                    "lat=${location.latitude}&lng=${location.longitude}&radius=${radius.toString()}"
            
            println("🌐 Fetching data from Refuge Restrooms API: $url")
            val response = URL(url).readText()
            println("📄 Raw response length: ${response.length}")
            println("📄 Response preview: ${response.take(500)}...")
            val dataArray = JSONArray(response)
            
            val restrooms = mutableListOf<ToiletLocation>()
            
            println("✅ Found ${dataArray.length()} restrooms from Refuge Restrooms API")
            
            for (i in 0 until dataArray.length()) {
                val restroom = dataArray.getJSONObject(i)
                
                val name = restroom.optString("name", "Public Restroom")
                val address = restroom.optString("street", "")
                val latitude = restroom.optDouble("latitude", 0.0)
                val longitude = restroom.optDouble("longitude", 0.0)
                
                val isGenderNeutral = restroom.optBoolean("unisex", false)
                val isBabyFriendly = restroom.optBoolean("changing_table", false)
                val isDogFriendly = false 
                val isWheelchairAccessible = restroom.optBoolean("accessible", false)
                val isFree = true 
                val isApproved = restroom.optBoolean("approved", true)
                val hasPaper = false 
                val hasSoap = false 
                val hasHandDryer = false 
                
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    latitude, longitude
                )
                
                if (distance <= radius.toFloat()) {
                    restrooms.add(
                        ToiletLocation(
                            id = "refuge_${restroom.optLong("id", 0)}",
                            name = name,
                            address = address,
                            latitude = latitude,
                            longitude = longitude,
                            isPublic = true,
                            isFree = isFree,
                            isGenderNeutral = isGenderNeutral,
                            isBabyFriendly = isBabyFriendly,
                            isDogFriendly = isDogFriendly,
                            isWheelchairAccessible = isWheelchairAccessible,
                            hasPaper = hasPaper,
                            hasSoap = hasSoap,
                            hasHandDryer = hasHandDryer,
                            distanceFromUser = distance,
                            submittedBy = "Refuge Restrooms",
                            isFromGooglePlaces = false,
                            isApproved = isApproved
                        )
                    )
                    
                    println("🚽 Refuge: $name - Gender Neutral: $isGenderNeutral, Baby Friendly: $isBabyFriendly, Accessible: $isWheelchairAccessible")
                }
            }
            
            val sortedRestrooms = restrooms.sortedBy { it.distanceFromUser }
            
            searchCache[cacheKey] = sortedRestrooms
            
            cleanCache()
            
            println("✅ Refuge Restrooms: Found ${sortedRestrooms.size} restrooms within ${radius}m radius")
            Result.success(sortedRestrooms)
            
        } catch (e: Exception) {
            println("❌ Error fetching Refuge Restrooms data: ${e.message}")
            println("❌ Error details: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }
    
    private fun cleanCache() {
        val currentTime = System.currentTimeMillis()
        searchCache.entries.removeIf { entry ->
            currentTime - entry.key.hashCode() > cacheTimeout
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }
} 