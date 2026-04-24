// ============================================================
// FILE: data/remote/LocationFirestoreDataSource.kt
// Purpose: Handles Firestore operations for location data
// ============================================================

package com.app.notisync_sender.data.remote

import android.util.Log
import com.app.notisync_sender.domain.model.DeviceLocation
import com.app.notisync_sender.domain.model.LocationRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "LocationFirestoreDS"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_LOCATION_REQUESTS = "location_requests"
        private const val COLLECTION_DEVICE_LOCATIONS = "device_locations"
    }

    suspend fun uploadLocation(location: DeviceLocation): Result<Unit> {
        return try {
            val locationMap = location.toFirestoreMap()

            firestore
                .collection(COLLECTION_USERS)
                .document(location.userId)
                .collection(COLLECTION_DEVICES)
                .document(location.deviceId)
                .collection(COLLECTION_DEVICE_LOCATIONS)
                .document(location.locationId)
                .set(locationMap)
                .await()

            Log.d(TAG, "Location uploaded: ${location.locationId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload location: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPendingLocationRequests(
        userId: String,
        deviceId: String
    ): Result<List<LocationRequest>> {
        return try {
            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_LOCATION_REQUESTS)
                .whereEqualTo("status", "pending")
                .orderBy("requestedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val requests = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.let { LocationRequest.fromFirestore(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing location request: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Found ${requests.size} pending location requests")
            Result.success(requests)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending requests: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observePendingLocationRequests(
        userId: String,
        deviceId: String
    ): Flow<List<LocationRequest>> = callbackFlow {
        val listenerRegistration = firestore
            .collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_DEVICES)
            .document(deviceId)
            .collection(COLLECTION_LOCATION_REQUESTS)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing location requests: ${error.message}")
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.data?.let { LocationRequest.fromFirestore(it) }
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(requests)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    suspend fun markRequestAsFulfilled(
        userId: String,
        deviceId: String,
        requestId: String
    ): Result<Unit> {
        return try {
            firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_LOCATION_REQUESTS)
                .document(requestId)
                .update(
                    mapOf(
                        "status" to "fulfilled",
                        "fulfilledAt" to System.currentTimeMillis()
                    )
                )
                .await()

            Log.d(TAG, "Request marked as fulfilled: $requestId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark request as fulfilled: ${e.message}", e)
            Result.failure(e)
        }
    }
}