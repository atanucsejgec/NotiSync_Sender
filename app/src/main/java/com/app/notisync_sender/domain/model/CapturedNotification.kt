// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/domain/model/CapturedNotification.kt
// Purpose: Represents a single notification captured from the device
// This is the core data unit flowing through the entire system
// ============================================================

package com.app.notisync_sender.domain.model

/**
 * CapturedNotification — Represents one notification captured by
 * NotificationListenerService from any app on the device.
 *
 * What: Immutable data class holding all relevant fields from a
 *       system notification — app name, title, message, and time.
 *
 * Why: Using a data class provides automatic equals(), hashCode(),
 *      copy(), and toString() — essential for duplicate filtering.
 *      The uniqueKey field pre-computes the dedup identity so the
 *      DuplicateFilter can compare notifications in O(1) time.
 *
 * @param appName      Package label of the app that posted the notification (e.g., "WhatsApp")
 * @param title        Notification title — usually the sender name (e.g., "John")
 * @param message      Notification body text — the actual message content (e.g., "Hello")
 * @param timestamp    Epoch milliseconds when the notification was captured
 * @param minuteKey    Timestamp truncated to the minute — used for same-minute dedup
 * @param uniqueKey    Composite key combining appName + title + message + minuteKey
 *                     Two notifications with the same uniqueKey are considered duplicates
 */
data class CapturedNotification(
    val appName: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val minuteKey: String,
    val uniqueKey: String
) {

    /**
     * Companion object provides a factory method to create CapturedNotification
     * with auto-computed minuteKey and uniqueKey fields.
     */
    companion object {

        /**
         * Creates a CapturedNotification with automatically computed
         * minuteKey and uniqueKey from the raw notification fields.
         *
         * What: Factory function that takes raw notification data and
         *       produces a fully initialized CapturedNotification.
         *
         * Why: The minuteKey and uniqueKey must be computed consistently
         *      across the app. Centralizing this logic in the factory
         *      prevents inconsistent key generation in different places.
         *
         * How: minuteKey is computed by dividing timestamp by 60000 (ms per minute),
         *      which groups all notifications within the same calendar minute.
         *      uniqueKey concatenates appName, title, message, and minuteKey
         *      with a pipe separator to create a collision-resistant identity.
         *
         * @param appName   Display name of the source app
         * @param title     Notification title text
         * @param message   Notification body text
         * @param timestamp Epoch milliseconds when notification was received
         * @return CapturedNotification with all fields populated
         */
        fun create(
            appName: String,
            title: String,
            message: String,
            timestamp: Long
        ): CapturedNotification {
            /* Truncate timestamp to minute precision — groups same-minute notifications */
            val minuteKey = (timestamp / 60000).toString()

            /* Build unique key by combining all identity fields with pipe separator */
            val uniqueKey = "$appName|$title|$message|$minuteKey"

            /* Return fully constructed notification with computed keys */
            return CapturedNotification(
                appName = appName,
                title = title,
                message = message,
                timestamp = timestamp,
                minuteKey = minuteKey,
                uniqueKey = uniqueKey
            )
        }
    }
}