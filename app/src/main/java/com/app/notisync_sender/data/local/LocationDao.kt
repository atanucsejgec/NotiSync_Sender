// ============================================================
// FILE: data/local/LocationDao.kt
// Purpose: DAO for location-related database operations
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingLocation(location: PendingLocationEntity)

    @Query("SELECT * FROM pending_locations WHERE is_synced = 0 ORDER BY created_at ASC")
    suspend fun getUnsyncedLocations(): List<PendingLocationEntity>

    @Query("SELECT COUNT(*) FROM pending_locations WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("UPDATE pending_locations SET is_synced = 1 WHERE location_id = :locationId")
    suspend fun markAsSynced(locationId: String)

    @Query("UPDATE pending_locations SET retry_count = retry_count + 1 WHERE location_id = :locationId")
    suspend fun incrementRetryCount(locationId: String)

    @Query("DELETE FROM pending_locations WHERE is_synced = 1")
    suspend fun deleteSyncedLocations()

    @Query("DELETE FROM pending_locations WHERE retry_count > :maxRetries AND is_synced = 0")
    suspend fun deleteFailedLocations(maxRetries: Int = 5)

    @Query("DELETE FROM pending_locations")
    suspend fun deleteAllLocations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcessedRequest(request: ProcessedLocationRequestEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM processed_location_requests WHERE request_id = :requestId)")
    suspend fun isRequestProcessed(requestId: String): Boolean

    @Query("DELETE FROM processed_location_requests WHERE processed_at < :cutoffTimestamp")
    suspend fun deleteOldProcessedRequests(cutoffTimestamp: Long)

    @Query("DELETE FROM processed_location_requests")
    suspend fun deleteAllProcessedRequests()
}