// ============================================================
// FILE: data/remote/FeatureFlagFirestoreDataSource.kt
// Purpose: Handles Firestore operations for feature flags
// ============================================================

package com.app.notisync_sender.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "FeatureFlagFirestoreDS"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_FEATURE_FLAGS = "feature_flags"
        private const val DOCUMENT_SETTINGS = "settings"
    }

    fun observeFeatureFlags(
        userId: String,
        deviceId: String
    ): Flow<FeatureFlags> = callbackFlow {
        val listenerRegistration: ListenerRegistration = firestore
            .collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_DEVICES)
            .document(deviceId)
            .collection(COLLECTION_FEATURE_FLAGS)
            .document(DOCUMENT_SETTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing feature flags: ${error.message}")
                    return@addSnapshotListener
                }

                val data = snapshot?.data
                val flags = if (data != null) {
                    FeatureFlags(
                        keyboardCaptureEnabled = data["keyboardCaptureEnabled"] as? Boolean ?: false,
                        locationSharingEnabled = data["locationSharingEnabled"] as? Boolean ?: true,
                        updatedAt = (data["updatedAt"] as? Long) ?: 0L
                    )
                } else {
                    FeatureFlags()
                }

                trySend(flags)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    suspend fun getFeatureFlags(
        userId: String,
        deviceId: String
    ): Result<FeatureFlags> {
        return try {
            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_FEATURE_FLAGS)
                .document(DOCUMENT_SETTINGS)
                .get()
                .await()

            val data = snapshot.data
            val flags = if (data != null) {
                FeatureFlags(
                    keyboardCaptureEnabled = data["keyboardCaptureEnabled"] as? Boolean ?: false,
                    locationSharingEnabled = data["locationSharingEnabled"] as? Boolean ?: true,
                    updatedAt = (data["updatedAt"] as? Long) ?: 0L
                )
            } else {
                FeatureFlags()
            }

            Result.success(flags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get feature flags: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateFeatureFlag(
        userId: String,
        deviceId: String,
        flagName: String,
        flagValue: Boolean
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                flagName to flagValue,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_FEATURE_FLAGS)
                .document(DOCUMENT_SETTINGS)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()

            Log.d(TAG, "Feature flag updated: $flagName = $flagValue")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update feature flag: ${e.message}", e)
            Result.failure(e)
        }
    }

    data class FeatureFlags(
        val keyboardCaptureEnabled: Boolean = false,
        val locationSharingEnabled: Boolean = true,
        val updatedAt: Long = 0L
    )
}