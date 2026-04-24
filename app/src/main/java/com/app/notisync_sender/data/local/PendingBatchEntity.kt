// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/data/local/PendingBatchEntity.kt
// Purpose: Room entity representing a notification batch waiting
// to be uploaded to Firebase — used for offline queue storage
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PendingBatchEntity — Room entity for offline notification batch storage.
 *
 * What: Represents a single batch of notifications stored in the local
 *       SQLite database when the device has no internet connection.
 *
 * Why: If the device is offline when the 60-second batch timer fires,
 *      the batch cannot be sent to Firebase. Instead, it is serialized
 *      to JSON and stored in this Room table. When internet returns,
 *      WorkManager reads pending batches from this table and uploads
 *      them to Firebase, then marks them as synced or deletes them.
 *
 * Table name: "pending_batches"
 *
 * @param batchId           Unique batch identifier (UUID) — serves as primary key
 * @param userId            Firebase Auth UID — identifies which user owns this batch
 * @param deviceId          Unique device identifier — identifies source device
 * @param deviceName        Human-readable device name (e.g., "Phone A")
 * @param batchTimestamp     Epoch milliseconds when the batch was assembled
 * @param notificationsJson JSON string containing the serialized list of notifications
 *                          Room cannot store List objects — Gson converts to/from JSON
 * @param isSynced          Whether this batch has been successfully uploaded to Firebase
 *                          false = pending upload, true = uploaded (safe to delete)
 * @param retryCount        Number of times upload has been attempted and failed
 *                          Used to implement exponential backoff or skip permanently
 * @param createdAt         Epoch milliseconds when this record was inserted into Room
 *                          Used for ordering and cleanup of old records
 */
@Entity(tableName = "pending_batches")
data class PendingBatchEntity(
    /* Primary key — UUID string uniquely identifies each batch */
    @PrimaryKey
    @ColumnInfo(name = "batch_id")
    val batchId: String,

    /* Firebase Auth user ID — associates batch with a user account */
    @ColumnInfo(name = "user_id")
    val userId: String,

    /* Device unique identifier — associates batch with a sender device */
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    /* Human-readable device name for display in Receiver App */
    @ColumnInfo(name = "device_name")
    val deviceName: String,

    /* Epoch milliseconds when the batch was originally assembled */
    @ColumnInfo(name = "batch_timestamp")
    val batchTimestamp: Long,

    /* JSON string of serialized notification list — stored as text in SQLite */
    @ColumnInfo(name = "notifications_json")
    val notificationsJson: String,

    /* Sync status flag — false means pending upload */
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    /* Number of failed upload attempts — used for retry logic */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    /* Epoch milliseconds when this record was inserted into Room */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)