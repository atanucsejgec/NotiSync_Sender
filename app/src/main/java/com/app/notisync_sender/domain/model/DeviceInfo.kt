// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/domain/model/DeviceInfo.kt
// Purpose: Holds device identification data used to tag
// notification batches with their source device
// ============================================================

package com.app.notisync_sender.domain.model

/**
 * DeviceInfo — Identifies the sender device in the system.
 *
 * What: Simple data class holding the unique device ID and
 *       human-readable device name.
 *
 * Why: The system supports multiple sender devices per user.
 *      Each notification batch must be tagged with the source
 *      device so the Receiver App can group and display
 *      notifications per device (e.g., "Phone A", "Phone B").
 *
 * @param deviceId    Unique device identifier — generated once and stored in SharedPreferences
 * @param deviceName  Human-readable name — defaults to Android device model name
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String
) {

    /**
     * Converts device info to a Firestore-compatible Map.
     *
     * What: Creates a Map for storing device registration data
     *       in the Firestore devices collection.
     *
     * Why: When a sender device first connects, it registers itself
     *      under users/{userId}/devices/{deviceId} so the Receiver
     *      App can discover and list all sender devices.
     *
     * @param lastSeen Epoch milliseconds of the last activity from this device
     * @return Map suitable for Firestore document write
     */
    fun toFirestoreMap(lastSeen: Long): Map<String, Any> {
        return mapOf(
            /* Unique device identifier */
            "deviceId" to deviceId,
            /* Display name shown in Receiver App device list */
            "deviceName" to deviceName,
            /* Last time this device sent a batch — used for online/offline status */
            "lastSeen" to lastSeen
        )
    }
}