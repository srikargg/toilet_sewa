package com.toiletseva.toiletseva.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlin.math.roundToInt

data class ToiletLocation(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val geoPoint: GeoPoint? = null,
    val placeType: PlaceType = PlaceType.PUBLIC_TOILET,
    val isPublic: Boolean = true,
    val isFree: Boolean = true,
    val isGenderNeutral: Boolean = false,
    val isBabyFriendly: Boolean = false,
    val isDogFriendly: Boolean = false,
    val isWheelchairAccessible: Boolean = false,
    val hasChangingTable: Boolean = false,
    val hasPaper: Boolean = false,
    val hasSoap: Boolean = false,
    val hasHandDryer: Boolean = false,
    val hasRunningWater: Boolean = false,
    val hasShower: Boolean = false,
    val cleanlinessRating: Double = 0.0,
    val availabilityRating: Double = 0.0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val submittedBy: String = "",
    val submittedAt: Timestamp? = null,
    val lastUpdated: Timestamp? = null,
    val isFromGooglePlaces: Boolean = false,
    val googlePlaceId: String? = null,
    val photos: List<String> = emptyList(),
    val distanceFromUser: Double = 0.0,
    val isApproved: Boolean = true,
    val isFiltered: Boolean = false
)

enum class PlaceType(val displayName: String) {
    PUBLIC_TOILET("🚻 Public Toilet"),
    RESTAURANT_CAFE("🏪 Restaurant / Café"),
    GAS_STATION("⛽ Gas Station"),
    METRO_TRAIN("🚉 Metro / Train Station"),
    HOTEL("🏨 Hotel")
}

data class ToiletReview(
    val id: String = "",
    val toiletId: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Double = 0.0,
    val comment: String = "",
    val createdAt: Timestamp? = null,
    val isHelpful: Int = 0
)

data class FilterOptions(
    val genderNeutralOnly: Boolean = false,
    val babyFriendlyOnly: Boolean = false,
    val dogFriendlyOnly: Boolean = false,
    val wheelchairAccessibleOnly: Boolean = false,
    val freeOnly: Boolean = false,
    val approvedOnly: Boolean = false,
    val minRating: Double = 0.0,
    val maxDistance: Double = 8.0
)

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0].toDouble()
}

fun formatDistance(meters: Double): String {
    val metersPerMile = 1609.34
    return when {
        meters < metersPerMile -> "${meters.toInt()} m"
        else -> {
            val miles = meters / metersPerMile
            "${"%.1f".format(miles)} mi"
        }
    }
} 