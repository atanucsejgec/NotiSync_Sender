// ============================================================
// FILE: data/local/PendingLocationEntity.kt
// Purpose: Room entity for storing locations pending upload
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_locations")
data class PendingLocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "location_id")
    val locationId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "device_name")
    val deviceName: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "provider")
    val provider: String,

    @ColumnInfo(name = "request_id")
    val requestId: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)