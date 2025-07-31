package com.toiletseva.toiletseva.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import android.location.Location
import com.google.android.gms.maps.model.LatLng

class FirebaseService {
    private val db = FirebaseFirestore.getInstance()
    private val toiletsCollection = db.collection("toilets")
    private val reviewsCollection = db.collection("reviews")
    
    fun getToiletsNearbyRealtime(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): Flow<List<ToiletLocation>> = callbackFlow {
        val query = toiletsCollection
            .limit(100)
        
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            
            val toilets = mutableListOf<ToiletLocation>()
            snapshot?.documents?.forEach { document ->
                val toilet = document.toObject(ToiletLocation::class.java)
                if (toilet != null) {
                    val distance = calculateDistance(
                        latitude, longitude,
                        toilet.latitude, toilet.longitude
                    )
                    
                    if (distance <= radiusMeters) {
                        toilets.add(toilet.copy(
                            id = document.id,
                            distanceFromUser = distance
                        ))
                    }
                }
            }
            
            trySend(toilets.sortedBy { it.distanceFromUser })
        }
        
        awaitClose { listener.remove() }
    }

    suspend fun addToilet(toilet: ToiletLocation): Result<String> {
        return try {
            val toiletData = toilet.copy(
                geoPoint = GeoPoint(toilet.latitude, toilet.longitude),
                submittedAt = Timestamp.now(),
                lastUpdated = Timestamp.now()
            )
            
            val docRef = toiletsCollection.add(toiletData).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getToiletsNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 10.0
    ): Result<List<ToiletLocation>> {
        return try {
            val center = GeoPoint(latitude, longitude)
            val radiusInDegrees = radiusKm / 111.0 

            val query = toiletsCollection
                .whereGreaterThan("geoPoint", GeoPoint(latitude - radiusInDegrees, longitude - radiusInDegrees))
                .whereLessThan("geoPoint", GeoPoint(latitude + radiusInDegrees, longitude + radiusInDegrees))
                .limit(50) 

            val snapshot = query.get().await()
            val toilets = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ToiletLocation::class.java)?.copy(id = doc.id)
            }
            Result.success(toilets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getToiletsNearby(location: LatLng, radiusMeters: Int): Result<List<ToiletLocation>> {
        return try {
            val querySnapshot = toiletsCollection
                .limit(100) 
                .get()
                .await()
            
            val toilets = mutableListOf<ToiletLocation>()
            
            for (document in querySnapshot.documents) {
                val toilet = document.toObject(ToiletLocation::class.java)
                if (toilet != null) {
                    val distance = calculateDistance(
                        location.latitude, location.longitude,
                        toilet.latitude, toilet.longitude
                    )
                    
                    if (distance <= radiusMeters) {
                        toilets.add(toilet.copy(
                            id = document.id,
                            distanceFromUser = distance
                        ))
                    }
                }
            }
            
            val sortedToilets = toilets.sortedBy { it.distanceFromUser }
            Result.success(sortedToilets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }

    suspend fun getToiletById(id: String): Result<ToiletLocation?> {
        return try {
            val doc = toiletsCollection.document(id).get().await()
            val toilet = doc.toObject(ToiletLocation::class.java)?.copy(id = doc.id)
            Result.success(toilet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateToilet(id: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            val updateData = updates.toMutableMap()
            updateData["lastUpdated"] = Timestamp.now()
            
            toiletsCollection.document(id).update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReview(toiletId: String, review: ToiletReview): Result<String> {
        return try {
            val reviewData = review.copy(
                toiletId = toiletId,
                createdAt = Timestamp.now()
            )

            val docRef = reviewsCollection.add(reviewData).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getToiletReviews(toiletId: String): Result<List<ToiletReview>> {
        return try {
            val snapshot = reviewsCollection
                .whereEqualTo("toiletId", toiletId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20) 
                .get()
                .await()
            
            val reviews = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ToiletReview::class.java)?.copy(id = doc.id)
            }
            Result.success(reviews)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateToiletRating(toiletId: String) {
        try {
            val reviews = getToiletReviews(toiletId).getOrNull() ?: return
            
            if (reviews.isNotEmpty()) {
                val averageRating = reviews.map { it.rating }.average()
                val reviewCount = reviews.size
                
                toiletsCollection.document(toiletId).update(
                    mapOf(
                        "rating" to averageRating,
                        "reviewCount" to reviewCount,
                        "lastUpdated" to Timestamp.now()
                    )
                ).await()
            }
        } catch (e: Exception) {
            println("Error updating toilet rating: ${e.message}")
        }
    }

    suspend fun deleteToilet(id: String): Result<Unit> {
        return try {
            val reviews = getToiletReviews(id).getOrNull() ?: emptyList()
            reviews.forEach { review ->
                reviewsCollection.document(review.id).delete().await()
            }
            
            toiletsCollection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 