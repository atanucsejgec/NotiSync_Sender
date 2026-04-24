// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/data/remote/FirestoreDataSource.kt
// Purpose: Handles all Firestore write operations for notification
// batches and device registration — the cloud data layer
// ============================================================

package com.app.notisync_sender.data.remote

import android.util.Log
import com.app.notisync_sender.domain.model.DeviceInfo
import com.app.notisync_sender.domain.model.NotificationBatch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirestoreDataSource — Encapsulates all Firestore write operations.
 *
 * What: Provides methods to write notification batches and register
 *       sender devices in the Firestore cloud database.
 *
 * Why: Separating Firestore operations into a dedicated data source
 *      follows the Repository pattern. The Repository orchestrates
 *      between this remote source and the local Room database,
 *      while this class only knows about Firestore.
 *
 * Firestore Schema:
 *   users/{userId}/
 *     ├── profile/          ← user metadata
 *     └── devices/{deviceId}/
 *           ├── deviceName, lastSeen  ← device registration
 *           └── notifications/{batchId}/
 *                 ├── batchTimestamp, notificationCount
 *                 └── notifications: [...]  ← batch payload
 *
 * All operations use Kotlin coroutines via .await() extension
 * from kotlinx-coroutines-play-services, which converts Firebase
 * Task objects into suspend functions.
 */
@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    /**
     * Tag for logcat logging — identifies FirestoreDataSource messages
     */
    companion object {
        private const val TAG = "FirestoreDataSource"

        /* Firestore collection path constants — centralized to avoid typos */
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_NOTIFICATIONS = "notifications"
    }

    /**
     * Uploads a notification batch to Firestore.
     *
     * What: Writes a single NotificationBatch as a document under
     *       users/{userId}/devices/{deviceId}/notifications/{batchId}
     *
     * Why: This is the primary data write operation. Called every 60 seconds
     *      by NotificationRepository when internet is available, or by
     *      OfflineSyncWorker when retrying previously failed uploads.
     *
     * How:
     *   1. Convert NotificationBatch to Map via toFirestoreMap()
     *   2. Navigate to the correct Firestore document path
     *   3. Use set() to write the document (creates or overwrites)
     *   4. await() suspends until Firestore confirms the write
     *
     * Error Handling: Returns Result.success(Unit) on success,
     *                 Result.failure(exception) on any Firestore error.
     *                 The caller (Repository) decides whether to retry
     *                 or queue the batch locally.
     *
     * @param batch The NotificationBatch to upload
     * @return Result indicating success or failure with exception
     */
    suspend fun uploadBatch(batch: NotificationBatch): Result<Unit> {
        return try {
            /* Convert batch to Firestore-compatible Map */
            val batchMap = batch.toFirestoreMap()

            /* Navigate to the document path and write the batch */
            firestore
                .collection(COLLECTION_USERS)
                .document(batch.userId)
                .collection(COLLECTION_DEVICES)
                .document(batch.deviceId)
                .collection(COLLECTION_NOTIFICATIONS)
                .document(batch.batchId)
                .set(batchMap)
                .await()

            Log.d(TAG, "Batch uploaded: ${batch.batchId} (${batch.notificationCount} notifications)")

            /* Return success */
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload batch ${batch.batchId}: ${e.message}", e)

            /* Return failure with the exception for caller to handle */
            Result.failure(e)
        }
    }

    /**
     * Registers or updates a sender device in Firestore.
     *
     * What: Writes device information (name, ID, lastSeen timestamp)
     *       under users/{userId}/devices/{deviceId}
     *
     * Why: The Receiver App needs to discover all sender devices
     *      registered under a user account. When a sender device
     *      starts syncing, it registers itself so the Receiver can
     *      list it. The lastSeen timestamp helps the Receiver show
     *      whether a sender device is currently active.
     *
     * How: Uses set() with merge option — updates existing fields
     *      without overwriting the entire document. This preserves
     *      the notifications subcollection if the device already exists.
     *
     * @param userId Firebase Auth UID of the logged-in user
     * @param deviceInfo Device identification (ID + name)
     * @return Result indicating success or failure
     */
    suspend fun registerDevice(
        userId: String,
        deviceInfo: DeviceInfo
    ): Result<Unit> {
        return try {
            /* Create device registration data with current timestamp */
            val deviceMap = deviceInfo.toFirestoreMap(
                lastSeen = System.currentTimeMillis()
            )

            /* Write to users/{userId}/devices/{deviceId} with merge */
            firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceInfo.deviceId)
                .set(deviceMap, com.google.firebase.firestore.SetOptions.merge())
                .await()

            Log.d(TAG, "Device registered: ${deviceInfo.deviceName} (${deviceInfo.deviceId})")

            /* Return success */
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register device: ${e.message}", e)

            /* Return failure */
            Result.failure(e)
        }
    }

    /**
     * Updates the lastSeen timestamp for a sender device.
     *
     * What: Updates only the lastSeen field on the device document
     *       without modifying other fields.
     *
     * Why: Called periodically (with each batch upload) so the Receiver
     *      App can determine if a sender device is currently active.
     *      A device with a recent lastSeen is shown as "online",
     *      while one with an old timestamp is shown as "offline".
     *
     * @param userId Firebase Auth UID
     * @param deviceId Unique device identifier
     * @return Result indicating success or failure
     */
    suspend fun updateDeviceLastSeen(
        userId: String,
        deviceId: String
    ): Result<Unit> {
        return try {
            /* Update only the lastSeen field using update() instead of set() */
            firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .update("lastSeen", System.currentTimeMillis())
                .await()

            Log.d(TAG, "Updated lastSeen for device: $deviceId")

            /* Return success */
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update lastSeen: ${e.message}", e)

            /* Return failure — non-critical, don't retry */
            Result.failure(e)
        }
    }

    /**
     * Creates or updates the user profile document in Firestore.
     *
     * What: Writes basic user information under users/{userId}/
     *
     * Why: Stores user metadata (email, creation timestamp) that
     *      may be needed for account management or admin purposes.
     *      Called once after successful registration or first login.
     *
     * @param userId Firebase Auth UID
     * @param email User's email address
     * @return Result indicating success or failure
     */
    suspend fun createUserProfile(
        userId: String,
        email: String
    ): Result<Unit> {
        return try {
            /* User profile data map */
            val profileMap = mapOf(
                "email" to email,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            /* Write to users/{userId} with merge to avoid overwriting devices */
            firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .set(profileMap, com.google.firebase.firestore.SetOptions.merge())
                .await()

            Log.d(TAG, "User profile created/updated for: $userId")

            /* Return success */
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create user profile: ${e.message}", e)

            /* Return failure */
            Result.failure(e)
        }
    }
}