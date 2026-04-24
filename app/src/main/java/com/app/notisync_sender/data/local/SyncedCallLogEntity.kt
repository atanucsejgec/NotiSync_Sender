// ============================================================
// FILE: data/local/SyncedCallLogEntity.kt
// Purpose: Tracks which call records have been synced to prevent duplicates
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "synced_call_logs")
data class SyncedCallLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "unique_key")
    val uniqueKey: String,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "batch_date")
    val batchDate: String
)