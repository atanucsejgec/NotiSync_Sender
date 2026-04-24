// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/domain/model/NotificationBatch.kt
// Purpose: Represents a batch of deduplicated notifications
// collected over a 60-second window, ready to be sent to Firebase
// ============================================================

package com.app.notisync_sender.domain.model

/**
 * NotificationBatch — A collection of deduplicated notifications
 * assembled during one 60-second batch window.
 *
 * What: Wraps a list of CapturedNotification objects along with
 *       metadata about the user, device, and batch timing.
 *
 * Why: Sending notifications one-by-one to Firebase would cause
 *      excessive read/write operations and high costs. Batching
 *      reduces Firestore writes from potentially hundreds per minute
 *      to exactly one write per minute. This is the cost optimization
 *      strategy described in the system requirements.
 *
 * @param batchId          Unique identifier for this batch (UUID or timestamp-based)
 * @param userId           Firebase Auth UID of the logged-in user
 * @param deviceId         Unique identifier for this sender device
 * @param deviceName       Human-readable name for this device (e.g., "Phone A")
 * @param batchTimestamp   Epoch milliseconds when the batch was assembled
 * @param notifications    List of deduplicated notifications in this batch
 * @param isSynced         Whether this batch has been successfully sent to Firebase
 *                         Used by the offline queue — false means pending upload
 */
data class NotificationBatch(
    val batchId: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val batchTimestamp: Long,
    val notifications: List<CapturedNotification>,
    val isSynced: Boolean = false
) {

    /**
     * Returns the number of notifications in this batch.
     *
     * What: Convenience property for quick access to batch size.
     *
     * Why: Used in UI to display batch statistics and in logging
     *      to track how many notifications were processed.
     */
    val notificationCount: Int
        get() = notifications.size

    /**
     * Returns true if this batch contains no notifications.
     *
     * What: Checks whether the notification list is empty.
     *
     * Why: Empty batches should not be sent to Firebase — this
     *      check prevents unnecessary Firestore writes when no
     *      notifications were captured during the 60-second window.
     */
    val isEmpty: Boolean
        get() = notifications.isEmpty()

    /**
     * Converts the batch to a Map suitable for Firestore document storage.
     *
     * What: Transforms all batch fields into a Map<String, Any> that
     *       Firestore can directly store as a document.
     *
     * Why: Firestore SDK accepts Map objects for document writes.
     *      Each notification is also converted to a Map, creating
     *      a nested structure that matches the Firestore schema
     *      defined in the system architecture.
     *
     * Firestore path: users/{userId}/devices/{deviceId}/notifications/{batchId}
     */
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            /* Batch-level metadata */
            "batchId" to batchId,
            "userId" to userId,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "batchTimestamp" to batchTimestamp,
            "notificationCount" to notificationCount,

            /* Convert each notification to a Map for nested storage */
            "notifications" to notifications.map { notification ->
                mapOf(
                    "appName" to notification.appName,
                    "title" to notification.title,
                    "message" to notification.message,
                    "timestamp" to notification.timestamp,
                    "minuteKey" to notification.minuteKey,
                    "uniqueKey" to notification.uniqueKey
                )
            }
        )
    }
}