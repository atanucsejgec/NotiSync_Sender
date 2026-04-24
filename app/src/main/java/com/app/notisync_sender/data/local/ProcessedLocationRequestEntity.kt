// ============================================================
// FILE: data/local/ProcessedLocationRequestEntity.kt
// Purpose: Tracks which location requests have been processed
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_location_requests")
data class ProcessedLocationRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "request_id")
    val requestId: String,

    @ColumnInfo(name = "processed_at")
    val processedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "was_successful")
    val wasSuccessful: Boolean = true
)