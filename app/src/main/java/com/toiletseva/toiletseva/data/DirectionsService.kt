package com.toiletseva.toiletseva.data

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

object PolylineDecoder {
    fun decode(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }
}

data class NavigationRoute(
    val distance: String,
    val duration: String,
    val durationInSeconds: Int,
    val steps: List<NavigationStep>,
    val polyline: String,
    val startLocation: LatLng,
    val endLocation: LatLng
)

data class NavigationStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val maneuver: String,
    val startLocation: LatLng,
    val endLocation: LatLng
)

enum class TransportationMode {
    DRIVING,
    WALKING,
    BICYCLING
}

class DirectionsService(private val context: Context) {
    private val apiKey = "YOUR_API_KEY"

    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TransportationMode = TransportationMode.DRIVING
    ): Result<NavigationRoute> = withContext(Dispatchers.IO) {
        try {
            val modeParam = when (mode) {
                TransportationMode.DRIVING -> "driving"
                TransportationMode.WALKING -> "walking"
                TransportationMode.BICYCLING -> "bicycling"
            }

            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&mode=$modeParam" +
                    "&key=$apiKey"

            println(" Getting directions: $url")
            val response = URL(url).readText()
            val json = JSONObject(response)

            val status = json.getString("status")
            println(" Directions API Response status: $status")

            if (status == "OK") {
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val legs = route.getJSONArray("legs")
                    val leg = legs.getJSONObject(0)

                    val distance = leg.getJSONObject("distance").getString("text")
                    val duration = leg.getJSONObject("duration").getString("text")
                    val durationInSeconds = leg.getJSONObject("duration").getInt("value")

                    val stepsArray = leg.getJSONArray("steps")
                    val steps = mutableListOf<NavigationStep>()

                    for (i in 0 until stepsArray.length()) {
                        val step = stepsArray.getJSONObject(i)
                        val stepDistance = step.getJSONObject("distance").getString("text")
                        val stepDuration = step.getJSONObject("duration").getString("text")
                        val instruction = step.getString("html_instructions")
                            .replace(Regex("<[^>]*>"), "")

                        val maneuver = step.optString("maneuver", "")

                        val startLocation = step.getJSONObject("start_location")
                        val endLocation = step.getJSONObject("end_location")

                        steps.add(NavigationStep(
                            instruction = instruction,
                            distance = stepDistance,
                            duration = stepDuration,
                            maneuver = maneuver,
                            startLocation = LatLng(
                                startLocation.getDouble("lat"),
                                startLocation.getDouble("lng")
                            ),
                            endLocation = LatLng(
                                endLocation.getDouble("lat"),
                                endLocation.getDouble("lng")
                            )
                        ))
                    }

                    val polyline = route.getJSONObject("overview_polyline").getString("points")

                    val startLocation = leg.getJSONObject("start_location")
                    val endLocation = leg.getJSONObject("end_location")

                    val navigationRoute = NavigationRoute(
                        distance = distance,
                        duration = duration,
                        durationInSeconds = durationInSeconds,
                        steps = steps,
                        polyline = polyline,
                        startLocation = LatLng(
                            startLocation.getDouble("lat"),
                            startLocation.getDouble("lng")
                        ),
                        endLocation = LatLng(
                            endLocation.getDouble("lat"),
                            endLocation.getDouble("lng")
                        )
                    )

                    println(" Found route: $distance in $duration")
                    Result.success(navigationRoute)
                } else {
                    Result.failure(Exception("No routes found"))
                }
            } else {
                val errorMessage = if (json.has("error_message")) {
                    json.getString("error_message")
                } else {
                    "Directions API error: $status"
                }
                println(" Directions API error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            println(" Exception getting directions: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getDirectionsWithAlternatives(
        origin: LatLng,
        destination: LatLng,
        mode: TransportationMode = TransportationMode.DRIVING
    ): Result<List<NavigationRoute>> = withContext(Dispatchers.IO) {
        try {
            val modeParam = when (mode) {
                TransportationMode.DRIVING -> "driving"
                TransportationMode.WALKING -> "walking"
                TransportationMode.BICYCLING -> "bicycling"
            }

            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&mode=$modeParam" +
                    "&alternatives=true" +
                    "&key=$apiKey"

            println(" Getting directions with alternatives: $url")
            val response = URL(url).readText()
            val json = JSONObject(response)

            val status = json.getString("status")
            println(" Directions API Response status: $status")

            if (status == "OK") {
                val routes = json.getJSONArray("routes")
                val navigationRoutes = mutableListOf<NavigationRoute>()

                for (routeIndex in 0 until routes.length()) {
                    val route = routes.getJSONObject(routeIndex)
                    val legs = route.getJSONArray("legs")
                    val leg = legs.getJSONObject(0)

                    val distance = leg.getJSONObject("distance").getString("text")
                    val duration = leg.getJSONObject("duration").getString("text")
                    val durationInSeconds = leg.getJSONObject("duration").getInt("value")

                    val stepsArray = leg.getJSONArray("steps")
                    val steps = mutableListOf<NavigationStep>()

                    for (i in 0 until stepsArray.length()) {
                        val step = stepsArray.getJSONObject(i)
                        val stepDistance = step.getJSONObject("distance").getString("text")
                        val stepDuration = step.getJSONObject("duration").getString("text")
                        val instruction = step.getString("html_instructions")
                            .replace(Regex("<[^>]*>"), "")

                        val maneuver = step.optString("maneuver", "")

                        val startLocation = step.getJSONObject("start_location")
                        val endLocation = step.getJSONObject("end_location")

                        steps.add(NavigationStep(
                            instruction = instruction,
                            distance = stepDistance,
                            duration = stepDuration,
                            maneuver = maneuver,
                            startLocation = LatLng(
                                startLocation.getDouble("lat"),
                                startLocation.getDouble("lng")
                            ),
                            endLocation = LatLng(
                                endLocation.getDouble("lat"),
                                endLocation.getDouble("lng")
                            )
                        ))
                    }

                    val polyline = route.getJSONObject("overview_polyline").getString("points")

                    val startLocation = leg.getJSONObject("start_location")
                    val endLocation = leg.getJSONObject("end_location")

                    val navigationRoute = NavigationRoute(
                        distance = distance,
                        duration = duration,
                        durationInSeconds = durationInSeconds,
                        steps = steps,
                        polyline = polyline,
                        startLocation = LatLng(
                            startLocation.getDouble("lat"),
                            startLocation.getDouble("lng")
                        ),
                        endLocation = LatLng(
                            endLocation.getDouble("lat"),
                            endLocation.getDouble("lng")
                        )
                    )

                    navigationRoutes.add(navigationRoute)
                }

                println("✅ Found ${navigationRoutes.size} alternative routes")
                Result.success(navigationRoutes)
            } else {
                val errorMessage = if (json.has("error_message")) {
                    json.getString("error_message")
                } else {
                    "Directions API error: $status"
                }
                println("❌ Directions API error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            println("💥 Exception getting directions: ${e.message}")
            Result.failure(e)
        }
    }
} 
