// ============================================================
// FILE: data/local/SentenceDao.kt
// Purpose: DAO for sentence batch database operations
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SentenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingBatch(batch: PendingSentenceBatchEntity)

    @Query("SELECT * FROM pending_sentence_batches WHERE is_synced = 0 ORDER BY created_at ASC")
    suspend fun getUnsyncedBatches(): List<PendingSentenceBatchEntity>

    @Query("SELECT COUNT(*) FROM pending_sentence_batches WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("UPDATE pending_sentence_batches SET is_synced = 1 WHERE batch_id = :batchId")
    suspend fun markAsSynced(batchId: String)

    @Query("UPDATE pending_sentence_batches SET retry_count = retry_count + 1 WHERE batch_id = :batchId")
    suspend fun incrementRetryCount(batchId: String)

    @Query("DELETE FROM pending_sentence_batches WHERE is_synced = 1")
    suspend fun deleteSyncedBatches()

    @Query("DELETE FROM pending_sentence_batches WHERE retry_count > :maxRetries AND is_synced = 0")
    suspend fun deleteFailedBatches(maxRetries: Int = 5)

    @Query("DELETE FROM pending_sentence_batches")
    suspend fun deleteAllBatches()
}