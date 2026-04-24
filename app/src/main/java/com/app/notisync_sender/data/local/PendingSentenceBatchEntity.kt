// ============================================================
// FILE: data/local/PendingSentenceBatchEntity.kt
// Purpose: Room entity for storing sentence batches pending upload
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sentence_batches")
data class PendingSentenceBatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "batch_id")
    val batchId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "device_name")
    val deviceName: String,

    @ColumnInfo(name = "batch_timestamp")
    val batchTimestamp: Long,

    @ColumnInfo(name = "sentences_json")
    val sentencesJson: String,

    @ColumnInfo(name = "sentence_count")
    val sentenceCount: Int,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)