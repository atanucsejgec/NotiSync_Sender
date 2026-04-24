// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/domain/processor/BatchProcessor.kt
// Purpose: Collects deduplicated notifications for 60 seconds
// then emits them as a single NotificationBatch
// ============================================================

package com.app.notisync_sender.domain.processor

import android.util.Log
import com.app.notisync_sender.domain.model.CapturedNotification
import com.app.notisync_sender.domain.model.DeviceInfo
import com.app.notisync_sender.domain.model.NotificationBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BatchProcessor — Collects notifications over a 60-second window,
 * deduplicates them, and emits a batch for upload to Firebase.
 *
 * What: Acts as the central coordinator between NotificationListenerService
 *       (which captures notifications) and NotificationRepository
 *       (which uploads them to Firebase or saves to Room).
 *
 * Why: Sending each notification individually would cause excessive
 *      Firestore write operations, increasing backend costs significantly.
 *      By collecting notifications for 60 seconds and sending them as
 *      one batch, we reduce writes from potentially hundreds to exactly
 *      one per minute per device.
 *
 * Flow:
 *   1. NotificationListenerService captures a notification
 *   2. Calls BatchProcessor.addNotification()
 *   3. DuplicateFilter checks if it's new
 *   4. If new → added to pendingNotifications list
 *   5. Timer runs every 60 seconds
 *   6. When timer fires → collect all pending → emit batch via SharedFlow
 *   7. Repository subscriber receives batch → uploads to Firebase or queues in Room
 *   8. DuplicateFilter and pendingNotifications are reset for next window
 *
 * Thread Safety: CopyOnWriteArrayList handles concurrent reads/writes.
 *                DuplicateFilter uses internal synchronization.
 */
@Singleton
class BatchProcessor @Inject constructor(
    private val duplicateFilter: DuplicateFilter
) {

    /**
     * Tag for logcat logging — identifies BatchProcessor messages
     */
    companion object {
        private const val TAG = "BatchProcessor"

        /** Batch interval in milliseconds — 60 seconds */
        const val BATCH_INTERVAL_MS = 60_000L
    }

    /**
     * Thread-safe list holding notifications collected during the current window.
     *
     * Why CopyOnWriteArrayList: It allows concurrent iteration and modification
     * without ConcurrentModificationException. Notifications can arrive from
     * NotificationListenerService while the timer thread reads the list to
     * assemble a batch. The copy-on-write strategy is efficient here because
     * reads (timer check) are far less frequent than writes (notification add).
     */
    private val pendingNotifications = CopyOnWriteArrayList<CapturedNotification>()

    /**
     * SharedFlow that emits completed batches to subscribers.
     *
     * What: A hot stream that emits NotificationBatch objects.
     *
     * Why: SharedFlow (not StateFlow) because we need to emit every batch
     *      as an event — even if two consecutive batches contain identical data.
     *      StateFlow would skip emission if the value hasn't changed.
     *      replay = 0 means late subscribers don't receive old batches.
     *      extraBufferCapacity = 5 allows up to 5 batches to buffer if
     *      the subscriber is temporarily slow (e.g., during Firestore write).
     */
    private val _batchFlow = MutableSharedFlow<NotificationBatch>(
        replay = 0,
        extraBufferCapacity = 5
    )

    /**
     * Public read-only SharedFlow that repositories subscribe to.
     * Emits a NotificationBatch every 60 seconds (if notifications exist).
     */
    val batchFlow: SharedFlow<NotificationBatch> = _batchFlow.asSharedFlow()

    /**
     * Coroutine scope for the batch timer — uses IO dispatcher for background work.
     * This scope lives as long as the BatchProcessor singleton exists.
     */
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Reference to the running timer coroutine job.
     * Used to cancel the timer when stopBatchTimer() is called.
     */
    private var timerJob: Job? = null

    /**
     * User ID from Firebase Auth — set when user logs in.
     * Included in every batch for Firestore path construction.
     */
    private var currentUserId: String = ""

    /**
     * Device info — set when the service starts.
     * Included in every batch to identify the source device.
     */
    private var currentDeviceInfo: DeviceInfo? = null

    /**
     * Tracks whether the batch timer is currently running.
     * Used to prevent starting multiple timers simultaneously.
     */
    private var isRunning = false

    /**
     * Configures the processor with user and device identity.
     *
     * What: Sets the userId and deviceInfo that will be attached
     *       to every batch produced by this processor.
     *
     * Why: The batch needs to know WHO (userId) and WHERE (deviceInfo)
     *      the notifications came from. This is set once when the
     *      foreground service starts after user login.
     *
     * @param userId    Firebase Auth UID of the logged-in user
     * @param deviceInfo Device identification (ID + name)
     */
    fun configure(userId: String, deviceInfo: DeviceInfo) {
        currentUserId = userId
        currentDeviceInfo = deviceInfo
        Log.d(TAG, "Configured — userId=$userId, device=${deviceInfo.deviceName}")
    }

    /**
     * Adds a new notification to the current batch window.
     *
     * What: Receives a captured notification, runs it through the
     *       DuplicateFilter, and if it's new, adds it to the
     *       pending list for the next batch.
     *
     * Why: This is the main entry point called by NotificationListenerService
     *      every time a system notification is received.
     *
     * How:
     *   1. DuplicateFilter checks uniqueKey against seenKeys HashSet
     *   2. If duplicate → log and discard
     *   3. If new → add to pendingNotifications list
     *
     * @param notification The captured notification to process
     */
    fun addNotification(notification: CapturedNotification) {
        /* Check if this notification is a duplicate within the current window */
        if (duplicateFilter.isNew(notification)) {
            /* Not a duplicate — add to pending list for next batch */
            pendingNotifications.add(notification)
            Log.d(TAG, "Added: ${notification.appName} — ${notification.title}: ${notification.message}")
        } else {
            /* Duplicate detected — discard silently */
            Log.d(TAG, "Duplicate filtered: ${notification.appName} — ${notification.title}")
        }
    }

    /**
     * Starts the 60-second batch timer.
     *
     * What: Launches a coroutine that runs an infinite loop with
     *       60-second delays. After each delay, it assembles and
     *       emits a batch from all pending notifications.
     *
     * Why: The timer ensures batches are sent at regular intervals
     *      regardless of notification volume. Even during quiet
     *      periods, the timer runs to maintain consistent sync cadence.
     *
     * Guard: If already running, this method returns immediately
     *        to prevent duplicate timers.
     */
    fun startBatchTimer() {
        /* Prevent starting multiple timers */
        if (isRunning) {
            Log.d(TAG, "Batch timer already running — skipping start")
            return
        }

        isRunning = true
        Log.d(TAG, "Starting batch timer — interval: ${BATCH_INTERVAL_MS}ms")

        /* Launch the timer coroutine on IO dispatcher */
        timerJob = scope.launch {
            /* Infinite loop — runs until stopBatchTimer() cancels the job */
            while (true) {
                /* Wait for the batch interval (60 seconds) */
                delay(BATCH_INTERVAL_MS)

                /* Assemble and emit the batch after the delay */
                emitCurrentBatch()
            }
        }
    }

    /**
     * Stops the batch timer and emits any remaining notifications.
     *
     * What: Cancels the timer coroutine and sends a final batch
     *       with any pending notifications.
     *
     * Why: Called when the foreground service is stopped or user
     *      logs out. We emit remaining notifications to prevent
     *      data loss — any captured notifications that haven't
     *      been batched yet should still be uploaded.
     */
    fun stopBatchTimer() {
        Log.d(TAG, "Stopping batch timer")
        isRunning = false

        /* Cancel the timer coroutine */
        timerJob?.cancel()
        timerJob = null

        /* Emit any remaining notifications as a final batch */
        scope.launch {
            emitCurrentBatch()
        }
    }

    /**
     * Assembles the current pending notifications into a batch and emits it.
     *
     * What: Takes a snapshot of all pending notifications, creates a
     *       NotificationBatch object, emits it via SharedFlow, then
     *       clears the pending list and resets the DuplicateFilter.
     *
     * Why: This is the core batch assembly logic. It runs every 60 seconds
     *      (from the timer) and also once when the timer stops (to flush
     *      remaining notifications).
     *
     * How:
     *   1. Snapshot pendingNotifications into a local list
     *   2. Clear pendingNotifications and reset DuplicateFilter
     *   3. If snapshot is empty → skip (don't send empty batches)
     *   4. Create NotificationBatch with metadata (userId, deviceId, etc.)
     *   5. Emit batch via _batchFlow SharedFlow
     *   6. Repository subscriber receives batch and handles upload/queue
     */
    private suspend fun emitCurrentBatch() {
        /* Take a snapshot of all pending notifications */
        val snapshot = ArrayList(pendingNotifications)

        /* Clear the pending list for the next window */
        pendingNotifications.clear()

        /* Reset the duplicate filter for the next window */
        duplicateFilter.reset()

        /* Skip empty batches — no notifications were captured in this window */
        if (snapshot.isEmpty()) {
            Log.d(TAG, "Batch window empty — no notifications to send")
            return
        }

        /* Get device info — if not configured, skip with warning */
        val deviceInfo = currentDeviceInfo
        if (deviceInfo == null) {
            Log.w(TAG, "Device info not configured — cannot create batch")
            return
        }

        /* Generate a unique batch ID using UUID */
        val batchId = UUID.randomUUID().toString()

        /* Create the batch object with all metadata and notifications */
        val batch = NotificationBatch(
            batchId = batchId,
            userId = currentUserId,
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            batchTimestamp = System.currentTimeMillis(),
            notifications = snapshot,
            isSynced = false
        )

        Log.d(TAG, "Emitting batch: ${batch.notificationCount} notifications, batchId=$batchId")

        /* Emit the batch via SharedFlow — repository subscriber will receive it */
        _batchFlow.emit(batch)
    }

    /**
     * Returns the number of pending notifications in the current window.
     *
     * What: Returns the size of the pendingNotifications list.
     *
     * Why: Used by the Dashboard UI to show real-time count of
     *      notifications waiting to be batched.
     */
    fun getPendingCount(): Int {
        return pendingNotifications.size
    }

    /**
     * Returns the number of unique notifications seen in the current window.
     *
     * What: Delegates to DuplicateFilter.currentUniqueCount().
     *
     * Why: Used for dashboard statistics — shows total unique notifications
     *      including those already batched in the current window.
     */
    fun getUniqueCount(): Int {
        return duplicateFilter.currentUniqueCount()
    }
}