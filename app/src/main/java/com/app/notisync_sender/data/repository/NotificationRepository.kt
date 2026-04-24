// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/data/repository/NotificationRepository.kt
// Purpose: Orchestrates notification batch handling — decides whether
// to upload to Firestore (online) or queue in Room (offline)
// ============================================================

package com.app.notisync_sender.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.app.notisync_sender.data.local.BatchTypeConverter
import com.app.notisync_sender.data.local.PendingBatchDao
import com.app.notisync_sender.data.local.PendingBatchEntity
import com.app.notisync_sender.data.remote.FirestoreDataSource
import com.app.notisync_sender.domain.model.CapturedNotification
import com.app.notisync_sender.domain.model.NotificationBatch
import com.app.notisync_sender.domain.processor.BatchProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationRepository — Central orchestrator for notification batch handling.
 *
 * What: Subscribes to BatchProcessor's batchFlow and handles each emitted
 *       batch by either uploading to Firestore (when online) or saving
 *       to Room database (when offline). Also provides methods for
 *       retrying failed batches and cleaning up synced data.
 *
 * Why: This is the single point of coordination between:
 *      - BatchProcessor (produces batches every 60 seconds)
 *      - FirestoreDataSource (cloud upload)
 *      - PendingBatchDao (local offline queue)
 *      - ConnectivityManager (network state detection)
 *
 *      By centralizing this logic in a repository, the services and
 *      ViewModels don't need to know about the online/offline decision.
 *      They simply produce or consume batches — the repository handles
 *      the rest.
 *
 * Flow:
 *   1. BatchProcessor emits a NotificationBatch via SharedFlow
 *   2. NotificationRepository receives it in the collector
 *   3. Check network connectivity via ConnectivityManager
 *   4. If online → upload to Firestore via FirestoreDataSource
 *   5. If offline OR upload fails → save to Room via PendingBatchDao
 *   6. WorkManager (OfflineSyncWorker) later retries failed batches
 */
@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreDataSource: FirestoreDataSource,
    private val pendingBatchDao: PendingBatchDao,
    private val batchProcessor: BatchProcessor
) {

    /**
     * Tag for logcat logging
     */
    companion object {
        private const val TAG = "NotificationRepository"
    }

    /**
     * Coroutine scope for background operations.
     * Uses IO dispatcher for database and network operations.
     */
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Flag indicating whether the repository is currently collecting
     * batches from BatchProcessor. Prevents multiple collectors.
     */
    private var isCollecting = false

    /**
     * Starts collecting batches from BatchProcessor's SharedFlow.
     *
     * What: Launches a coroutine that continuously collects from
     *       batchProcessor.batchFlow and handles each emitted batch.
     *
     * Why: The repository must actively subscribe to the BatchProcessor
     *      to receive batches. This method is called when the foreground
     *      service starts (after user login). The collector runs
     *      indefinitely until stopCollecting() is called.
     *
     * Guard: If already collecting, returns immediately to prevent
     *        multiple parallel collectors (which would cause duplicate
     *        uploads).
     */
    fun startCollecting() {
        /* Prevent starting multiple collectors */
        if (isCollecting) {
            Log.d(TAG, "Already collecting batches — skipping start")
            return
        }

        isCollecting = true
        Log.d(TAG, "Starting batch collection from BatchProcessor")

        /* Launch collector coroutine on IO dispatcher */
        scope.launch {
            /* Collect indefinitely from the SharedFlow */
            batchProcessor.batchFlow.collect { batch ->
                /* Handle each batch — upload or queue */
                handleBatch(batch)
            }
        }
    }

    /**
     * Stops collecting batches (cleanup when service stops).
     *
     * What: Marks the repository as no longer collecting.
     *
     * Why: Called when the foreground service is stopped. The scope
     *      coroutine will be cancelled when the service is destroyed,
     *      but this flag ensures we can restart cleanly.
     *
     * Note: The actual coroutine cancellation happens when the
     *       SyncForegroundService's lifecycle ends. This method
     *       just resets the guard flag for potential restart.
     */
    fun stopCollecting() {
        isCollecting = false
        Log.d(TAG, "Stopped batch collection")
    }

    /**
     * Handles a single batch — uploads to Firestore or queues locally.
     *
     * What: The core decision logic that determines how to process
     *       each batch based on network availability and upload success.
     *
     * Why: This method implements the offline-first strategy:
     *      1. Try to upload if online
     *      2. Fall back to local storage if offline or upload fails
     *      This ensures no notification data is ever lost due to
     *      network issues.
     *
     * Flow:
     *   1. Check if device is online via isNetworkAvailable()
     *   2. If offline → save to Room immediately (skip upload attempt)
     *   3. If online → attempt Firestore upload
     *   4. If upload succeeds → update device lastSeen timestamp
     *   5. If upload fails → save to Room for later retry
     *
     * @param batch The NotificationBatch to process
     */
    private suspend fun handleBatch(batch: NotificationBatch) {
        Log.d(TAG, "Handling batch: ${batch.batchId} (${batch.notificationCount} notifications)")

        /* Check network connectivity */
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Offline — queueing batch locally: ${batch.batchId}")
            saveBatchToLocalQueue(batch)
            return
        }

        /* Online — attempt Firestore upload */
        val uploadResult = firestoreDataSource.uploadBatch(batch)

        if (uploadResult.isSuccess) {
            /* Upload succeeded — update device lastSeen timestamp */
            Log.d(TAG, "Batch uploaded successfully: ${batch.batchId}")

            /* Update device lastSeen (non-critical, ignore failures) */
            firestoreDataSource.updateDeviceLastSeen(
                userId = batch.userId,
                deviceId = batch.deviceId
            )
        } else {
            /* Upload failed — save to local queue for retry */
            Log.w(TAG, "Upload failed — queueing batch locally: ${batch.batchId}")
            saveBatchToLocalQueue(batch)
        }
    }

    /**
     * Saves a batch to the local Room database for later retry.
     *
     * What: Converts a NotificationBatch to PendingBatchEntity and
     *       inserts it into the pending_batches table.
     *
     * Why: When the device is offline or Firestore upload fails,
     *      the batch must be persisted locally so it can be retried
     *      later by WorkManager. Room provides durable storage that
     *      survives app restarts and device reboots.
     *
     * Conversion: NotificationBatch → PendingBatchEntity requires
     *            serializing the notifications list to JSON string
     *            via BatchTypeConverter, since Room cannot store
     *            List objects directly.
     *
     * @param batch The NotificationBatch to save locally
     */
    private suspend fun saveBatchToLocalQueue(batch: NotificationBatch) {
        try {
            /* Convert notification list to JSON string */
            val notificationsJson = BatchTypeConverter.toJson(batch.notifications)

            /* Create the Room entity */
            val entity = PendingBatchEntity(
                batchId = batch.batchId,
                userId = batch.userId,
                deviceId = batch.deviceId,
                deviceName = batch.deviceName,
                batchTimestamp = batch.batchTimestamp,
                notificationsJson = notificationsJson,
                isSynced = false,
                retryCount = 0,
                createdAt = System.currentTimeMillis()
            )

            /* Insert into Room database */
            pendingBatchDao.insertBatch(entity)

            Log.d(TAG, "Batch saved to local queue: ${batch.batchId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save batch to local queue: ${e.message}", e)
        }
    }

    /**
     * Retries uploading all unsynced batches from the local queue.
     *
     * What: Reads all pending batches from Room, attempts to upload
     *       each one to Firestore, and marks successful ones as synced.
     *
     * Why: Called by OfflineSyncWorker when internet becomes available.
     *      This ensures batches that were queued during offline periods
     *      eventually get uploaded to Firebase. The method processes
     *      batches in chronological order (oldest first) to maintain
     *      notification ordering on the Receiver side.
     *
     * Flow:
     *   1. Query all unsynced batches from Room (oldest first)
     *   2. For each batch:
     *      a. Convert PendingBatchEntity → NotificationBatch
     *      b. Attempt Firestore upload
     *      c. If success → mark as synced in Room
     *      d. If failure → increment retry count
     *   3. After all attempts, delete synced batches from Room
     *   4. Delete batches that exceeded max retry count
     *
     * @return Number of batches successfully uploaded
     */
    suspend fun retryPendingBatches(): Int {
        Log.d(TAG, "Retrying pending batches...")

        /* Get all unsynced batches ordered by creation time */
        val pendingBatches = pendingBatchDao.getUnsyncedBatches()

        if (pendingBatches.isEmpty()) {
            Log.d(TAG, "No pending batches to retry")
            return 0
        }

        Log.d(TAG, "Found ${pendingBatches.size} pending batches to retry")

        var successCount = 0

        /* Process each pending batch */
        for (entity in pendingBatches) {
            /* Convert entity back to domain model */
            val batch = entityToBatch(entity)

            /* Attempt Firestore upload */
            val uploadResult = firestoreDataSource.uploadBatch(batch)

            if (uploadResult.isSuccess) {
                /* Upload succeeded — mark as synced */
                pendingBatchDao.markAsSynced(entity.batchId)
                successCount++
                Log.d(TAG, "Retry succeeded for batch: ${entity.batchId}")

                /* Update device lastSeen */
                firestoreDataSource.updateDeviceLastSeen(
                    userId = batch.userId,
                    deviceId = batch.deviceId
                )
            } else {
                /* Upload failed — increment retry count */
                pendingBatchDao.incrementRetryCount(entity.batchId)
                Log.w(TAG, "Retry failed for batch: ${entity.batchId}")
            }
        }

        /* Cleanup: delete successfully synced batches */
        pendingBatchDao.deleteSyncedBatches()

        /* Cleanup: delete batches that exceeded max retries (5 attempts) */
        pendingBatchDao.deleteFailedBatches(maxRetries = 5)

        Log.d(TAG, "Retry complete: $successCount/${pendingBatches.size} succeeded")

        return successCount
    }

    /**
     * Converts a PendingBatchEntity back to a NotificationBatch.
     *
     * What: Deserializes the JSON notification list and constructs
     *       a NotificationBatch domain object.
     *
     * Why: Room stores data as PendingBatchEntity with JSON string.
     *      Firestore upload requires the NotificationBatch domain model.
     *      This conversion bridges the two representations.
     *
     * @param entity The PendingBatchEntity from Room
     * @return NotificationBatch domain object ready for upload
     */
    private fun entityToBatch(entity: PendingBatchEntity): NotificationBatch {
        /* Deserialize JSON string back to notification list */
        val notifications: List<CapturedNotification> = BatchTypeConverter.fromJson(
            entity.notificationsJson
        )

        /* Construct and return the domain model */
        return NotificationBatch(
            batchId = entity.batchId,
            userId = entity.userId,
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            batchTimestamp = entity.batchTimestamp,
            notifications = notifications,
            isSynced = entity.isSynced
        )
    }

    /**
     * Checks if the device currently has internet connectivity.
     *
     * What: Queries ConnectivityManager to determine if any network
     *       with internet capability is currently available.
     *
     * Why: Before attempting a Firestore upload, we check connectivity
     *      to avoid unnecessary network timeouts. If offline, we
     *      immediately queue the batch locally instead of waiting
     *      for the upload to fail after a long timeout.
     *
     * How: Uses NetworkCapabilities API (Android 6.0+) to check for
     *      NET_CAPABILITY_INTERNET on the active network. This is
     *      more reliable than the deprecated NetworkInfo API.
     *
     * @return true if internet is available, false otherwise
     */
    private fun isNetworkAvailable(): Boolean {
        /* Get ConnectivityManager system service */
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        /* Get the currently active network */
        val activeNetwork = connectivityManager.activeNetwork ?: return false

        /* Get the capabilities of the active network */
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return false

        /* Check if the network has internet capability */
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Observes the count of pending (unsynced) batches as a Flow.
     *
     * What: Returns a Flow that emits the current count whenever
     *       the pending_batches table changes.
     *
     * Why: The Dashboard UI displays the number of batches waiting
     *      to be uploaded. Using Flow ensures the UI automatically
     *      updates when batches are added or removed — no manual
     *      refresh needed.
     *
     * @return Flow emitting the count of unsynced batches
     */
    fun observePendingBatchCount(): Flow<Int> {
        return pendingBatchDao.observeUnsyncedCount()
    }

    /**
     * Deletes all local data (called on logout).
     *
     * What: Clears all pending batches from the Room database.
     *
     * Why: When the user logs out, all local data should be cleared
     *      to prevent data from leaking to a different user account.
     *      Pending batches belong to the logged-out user and should
     *      not be uploaded under a different account.
     */
    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing all local data")
        pendingBatchDao.deleteAllBatches()
    }

    /**
     * Registers the sender device with Firestore.
     *
     * What: Writes device information to users/{userId}/devices/{deviceId}
     *
     * Why: Called when the sync service starts. The Receiver App needs
     *      to discover all sender devices under a user account. This
     *      registration makes the device visible in the Receiver's
     *      device list.
     *
     * @param userId Firebase Auth UID
     * @param deviceInfo Device identification (ID + name)
     */
    suspend fun registerDevice(
        userId: String,
        deviceInfo: com.app.notisync_sender.domain.model.DeviceInfo
    ) {
        val result = firestoreDataSource.registerDevice(userId, deviceInfo)
        if (result.isSuccess) {
            Log.d(TAG, "Device registered: ${deviceInfo.deviceName}")
        } else {
            Log.e(TAG, "Failed to register device: ${result.exceptionOrNull()?.message}")
        }
    }
}