// ============================================================
// FILE: data/local/CallLogDao.kt
// Purpose: DAO for call log database operations
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingBatch(batch: PendingCallLogBatchEntity)

    @Query("SELECT * FROM pending_call_log_batches WHERE is_synced = 0 ORDER BY created_at ASC")
    suspend fun getUnsyncedBatches(): List<PendingCallLogBatchEntity>

    @Query("SELECT COUNT(*) FROM pending_call_log_batches WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("UPDATE pending_call_log_batches SET is_synced = 1 WHERE batch_id = :batchId")
    suspend fun markAsSynced(batchId: String)

    @Query("UPDATE pending_call_log_batches SET retry_count = retry_count + 1 WHERE batch_id = :batchId")
    suspend fun incrementRetryCount(batchId: String)

    @Query("DELETE FROM pending_call_log_batches WHERE is_synced = 1")
    suspend fun deleteSyncedBatches()

    @Query("DELETE FROM pending_call_log_batches WHERE retry_count > :maxRetries AND is_synced = 0")
    suspend fun deleteFailedBatches(maxRetries: Int = 5)

    @Query("DELETE FROM pending_call_log_batches")
    suspend fun deleteAllBatches()

    @Query("SELECT * FROM pending_call_log_batches WHERE batch_date = :batchDate LIMIT 1")
    suspend fun getBatchByDate(batchDate: String): PendingCallLogBatchEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncedCallLog(syncedLog: SyncedCallLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncedCallLogs(syncedLogs: List<SyncedCallLogEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM synced_call_logs WHERE unique_key = :uniqueKey)")
    suspend fun isCallLogSynced(uniqueKey: String): Boolean

    @Query("SELECT unique_key FROM synced_call_logs WHERE batch_date = :batchDate")
    suspend fun getSyncedKeysForDate(batchDate: String): List<String>

    @Query("DELETE FROM synced_call_logs WHERE synced_at < :cutoffTimestamp")
    suspend fun deleteOldSyncedLogs(cutoffTimestamp: Long)

    @Query("DELETE FROM synced_call_logs")
    suspend fun deleteAllSyncedLogs()

    @Query("SELECT COUNT(*) FROM synced_call_logs")
    suspend fun getTotalSyncedCount(): Int
}