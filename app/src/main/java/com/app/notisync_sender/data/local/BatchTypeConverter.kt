// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/data/local/BatchTypeConverter.kt
// Purpose: Room TypeConverter that converts NotificationBatch
// notification lists to/from JSON strings for SQLite storage
// ============================================================

package com.app.notisync_sender.data.local

import com.app.notisync_sender.domain.model.CapturedNotification
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * BatchTypeConverter — Converts notification lists to/from JSON
 * for Room database storage.
 *
 * What: Provides two static utility methods:
 *       - toJson(): Converts List<CapturedNotification> → JSON String
 *       - fromJson(): Converts JSON String → List<CapturedNotification>
 *
 * Why: Room/SQLite cannot natively store List objects or complex types.
 *      The notifications field in NotificationBatch is a List<CapturedNotification>,
 *      which must be serialized to a JSON string before storing in the
 *      notificationsJson column. When reading from the database, the
 *      JSON string is deserialized back to a List.
 *
 * Why Gson: Gson is lightweight, well-tested, and has no annotation
 *           processing overhead. It handles generic types via TypeToken
 *           and integrates cleanly with Room without requiring additional
 *           plugins or code generation.
 *
 * Note: These are NOT Room @TypeConverter annotations — they are
 *       utility functions called manually by the Repository when
 *       converting between NotificationBatch and PendingBatchEntity.
 *       This gives us explicit control over serialization rather
 *       than relying on automatic Room type conversion.
 */
object BatchTypeConverter {

    /**
     * Gson instance — reused across all conversions for efficiency.
     * Thread-safe by default — Gson instances are immutable after creation.
     */
    private val gson = Gson()

    /**
     * TypeToken for List<CapturedNotification> — used by Gson to
     * properly deserialize generic types at runtime.
     *
     * Why TypeToken: Java/Kotlin generics are erased at runtime.
     *               Without TypeToken, Gson would not know that the
     *               List contains CapturedNotification objects and
     *               would deserialize them as LinkedTreeMap instead.
     */
    private val listType = object : TypeToken<List<CapturedNotification>>() {}.type

    /**
     * Converts a list of CapturedNotification objects to a JSON string.
     *
     * What: Serializes the notification list into a compact JSON string.
     *
     * Why: Called when creating a PendingBatchEntity from a NotificationBatch.
     *      The resulting JSON string is stored in the notificationsJson column.
     *
     * Example output:
     * [{"appName":"WhatsApp","title":"John","message":"Hello","timestamp":1703526065000,...}]
     *
     * @param notifications List of captured notifications to serialize
     * @return JSON string representation of the notification list
     */
    fun toJson(notifications: List<CapturedNotification>): String {
        return gson.toJson(notifications)
    }

    /**
     * Converts a JSON string back to a list of CapturedNotification objects.
     *
     * What: Deserializes a JSON string into a List<CapturedNotification>.
     *
     * Why: Called when converting a PendingBatchEntity back to a
     *      NotificationBatch for upload to Firebase.
     *
     * Safety: If the JSON is null or empty, returns an empty list
     *         instead of throwing an exception. This handles edge
     *         cases where the database might contain corrupted data.
     *
     * @param json JSON string to deserialize
     * @return List of CapturedNotification objects, or empty list if json is invalid
     */
    fun fromJson(json: String?): List<CapturedNotification> {
        /* Return empty list for null or blank JSON strings */
        if (json.isNullOrBlank()) return emptyList()

        return try {
            /* Deserialize using TypeToken to preserve generic type information */
            gson.fromJson(json, listType)
        } catch (e: Exception) {
            /* If deserialization fails, return empty list rather than crashing */
            emptyList()
        }
    }
}