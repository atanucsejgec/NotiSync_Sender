// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/data/local/PendingBatchDao.kt
// Purpose: Data Access Object for pending batch CRUD operations
// All queries run on background threads via suspend functions
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * PendingBatchDao — Room DAO providing all database operations
 * for the pending_batches table.
 *
 * What: Defines suspend functions for inserting, querying, updating,
 *       and deleting pending notification batches.
 *
 * Why: Room generates the actual SQL implementation at compile time
 *      via KSP. Using suspend functions ensures all database operations
 *      run on coroutine dispatchers (not the main thread), preventing
 *      UI freezes. Flow-based queries provide real-time observation
 *      of pending batch count for the dashboard UI.
 */
@Dao
interface PendingBatchDao {

    /**
     * Inserts a new pending batch into the database.
     *
     * What: Stores a batch that could not be sent to Firebase due to
     *       no internet connection.
     *
     * Why: REPLACE strategy ensures that if a batch with the same ID
     *      somehow gets inserted twice (edge case during retry), it
     *      overwrites rather than throwing an error.
     *
     * @param batch The PendingBatchEntity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: PendingBatchEntity)

    /**
     * Retrieves all unsynced batches ordered by creation time.
     *
     * What: Returns all batches where isSynced = false, oldest first.
     *
     * Why: WorkManager calls this when internet becomes available
     *      to upload all pending batches. Oldest-first ordering
     *      ensures chronological delivery to the Receiver App.
     *
     * @return List of unsynced PendingBatchEntity objects
     */
    @Query("SELECT * FROM pending_batches WHERE is_synced = 0 ORDER BY created_at ASC")
    suspend fun getUnsyncedBatches(): List<PendingBatchEntity>

    /**
     * Observes the count of unsynced batches as a Flow.
     *
     * What: Returns a Flow that emits the current count of pending
     *       (unsynced) batches whenever the count changes.
     *
     * Why: The Dashboard UI observes this Flow to show the user how
     *      many batches are waiting to be uploaded. Flow automatically
     *      re-emits when Room detects a table change (insert/delete/update).
     *
     * @return Flow emitting the count of unsynced batches
     */
    @Query("SELECT COUNT(*) FROM pending_batches WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    /**
     * Marks a batch as successfully synced to Firebase.
     *
     * What: Updates the isSynced flag to true for a specific batch.
     *
     * Why: After WorkManager successfully uploads a batch to Firestore,
     *      it marks the batch as synced. This prevents re-uploading
     *      the same batch on the next sync cycle.
     *
     * @param batchId The unique ID of the batch to mark as synced
     */
    @Query("UPDATE pending_batches SET is_synced = 1 WHERE batch_id = :batchId")
    suspend fun markAsSynced(batchId: String)

    /**
     * Increments the retry count for a failed batch upload.
     *
     * What: Adds 1 to the retry_count column for a specific batch.
     *
     * Why: Tracks how many times a batch upload has been attempted.
     *      If retry count exceeds a threshold (e.g., 5), the batch
     *      can be skipped or flagged for manual review to prevent
     *      infinite retry loops.
     *
     * @param batchId The unique ID of the batch that failed upload
     */
    @Query("UPDATE pending_batches SET retry_count = retry_count + 1 WHERE batch_id = :batchId")
    suspend fun incrementRetryCount(batchId: String)

    /**
     * Deletes all batches that have been successfully synced.
     *
     * What: Removes all records where isSynced = true.
     *
     * Why: Cleanup operation to free local storage space. Once a batch
     *      has been uploaded to Firebase, keeping it locally is unnecessary.
     *      Called periodically by WorkManager or after successful sync.
     */
    @Query("DELETE FROM pending_batches WHERE is_synced = 1")
    suspend fun deleteSyncedBatches()

    /**
     * Deletes batches that have exceeded the maximum retry count.
     *
     * What: Removes all unsynced batches where retry_count exceeds
     *       the specified maximum.
     *
     * Why: Prevents permanently stuck batches from accumulating in
     *      the database. If a batch fails to upload after 5 attempts,
     *      it is likely corrupted or the Firestore rules reject it.
     *      Removing it prevents storage bloat on low-end devices.
     *
     * @param maxRetries Maximum number of retries before deletion
     */
    @Query("DELETE FROM pending_batches WHERE is_synced = 0 AND retry_count > :maxRetries")
    suspend fun deleteFailedBatches(maxRetries: Int = 5)

    /**
     * Deletes all batches from the table.
     *
     * What: Removes every record from the pending_batches table.
     *
     * Why: Called when the user logs out — all local data should be
     *      cleared to prevent data leaking between user accounts.
     */
    @Query("DELETE FROM pending_batches")
    suspend fun deleteAllBatches()

    /**
     * Returns the total count of all batches in the database.
     *
     * What: Counts all records regardless of sync status.
     *
     * Why: Used for debugging and diagnostics — shows total batches
     *      including synced ones that haven't been cleaned up yet.
     *
     * @return Total number of batches stored locally
     */
    @Query("SELECT COUNT(*) FROM pending_batches")
    suspend fun getTotalBatchCount(): Int
}