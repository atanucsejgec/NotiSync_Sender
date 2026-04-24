// ============================================================
// FILE: data/repository/CallLogRepository.kt
// Purpose: Orchestrates call log operations with offline support
// ============================================================

package com.app.notisync_sender.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.app.notisync_sender.data.local.CallLogDao
import com.app.notisync_sender.data.local.CallLogTypeConverter
import com.app.notisync_sender.data.local.PendingCallLogBatchEntity
import com.app.notisync_sender.data.local.SyncedCallLogEntity
import com.app.notisync_sender.data.remote.CallLogFirestoreDataSource
import com.app.notisync_sender.domain.model.CallLogBatch
import com.app.notisync_sender.domain.model.CallRecord
import com.app.notisync_sender.domain.model.DeviceInfo
import com.app.notisync_sender.service.calllog.CallLogReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callLogFirestoreDataSource: CallLogFirestoreDataSource,
    private val callLogDao: CallLogDao,
    private val callLogReader: CallLogReader,
    private val authRepository: AuthRepository,
    private val deviceInfo: DeviceInfo
) {

    companion object {
        private const val TAG = "CallLogRepository"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    suspend fun syncDailyCallLogs(): Result<Int> {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Log.e(TAG, "No user logged in")
            return Result.failure(Exception("User not logged in"))
        }

        if (!callLogReader.hasCallLogPermission()) {
            Log.e(TAG, "Call log permission not granted")
            return Result.failure(SecurityException("Call log permission not granted"))
        }

        val callLogsResult = callLogReader.getCallLogsForLast24Hours()
        if (callLogsResult.isFailure) {
            return Result.failure(callLogsResult.exceptionOrNull() ?: Exception("Failed to read call logs"))
        }

        val allCalls = callLogsResult.getOrThrow()
        Log.d(TAG, "Read ${allCalls.size} call logs from device")

        val today = dateFormat.format(System.currentTimeMillis())
        val syncedKeys = callLogDao.getSyncedKeysForDate(today).toSet()

        val newCalls = allCalls.filter { call ->
            !syncedKeys.contains(call.uniqueKey)
        }

        Log.d(TAG, "Found ${newCalls.size} new calls to sync (filtered ${allCalls.size - newCalls.size} duplicates)")

        if (newCalls.isEmpty()) {
            Log.d(TAG, "No new calls to sync")
            return Result.success(0)
        }

        val batch = CallLogBatch.create(
            userId = userId,
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            calls = newCalls
        )

        return uploadOrQueueBatch(batch)
    }

    private suspend fun uploadOrQueueBatch(batch: CallLogBatch): Result<Int> {
        if (isNetworkAvailable()) {
            val uploadResult = callLogFirestoreDataSource.uploadCallLogBatch(batch)

            if (uploadResult.isSuccess) {
                Log.d(TAG, "Call log batch uploaded successfully")
                markCallsAsSynced(batch)
                return Result.success(batch.callCount)
            } else {
                Log.w(TAG, "Upload failed — saving to local queue")
                saveBatchToLocalQueue(batch)
                return Result.success(batch.callCount)
            }
        } else {
            Log.d(TAG, "Offline — saving batch to local queue")
            saveBatchToLocalQueue(batch)
            return Result.success(batch.callCount)
        }
    }

    private suspend fun saveBatchToLocalQueue(batch: CallLogBatch) {
        try {
            val callsJson = CallLogTypeConverter.toJson(batch.calls)

            val entity = PendingCallLogBatchEntity(
                batchId = batch.batchId,
                userId = batch.userId,
                deviceId = batch.deviceId,
                deviceName = batch.deviceName,
                batchDate = batch.batchDate,
                batchTimestamp = batch.batchTimestamp,
                callsJson = callsJson,
                callCount = batch.callCount,
                isSynced = false,
                retryCount = 0
            )

            callLogDao.insertPendingBatch(entity)
            Log.d(TAG, "Batch saved to local queue: ${batch.batchId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save batch to local queue: ${e.message}", e)
        }
    }

    private suspend fun markCallsAsSynced(batch: CallLogBatch) {
        try {
            val syncedEntities = batch.calls.map { call ->
                SyncedCallLogEntity(
                    uniqueKey = call.uniqueKey,
                    syncedAt = System.currentTimeMillis(),
                    batchDate = batch.batchDate
                )
            }
            callLogDao.insertSyncedCallLogs(syncedEntities)
            Log.d(TAG, "Marked ${syncedEntities.size} calls as synced")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark calls as synced: ${e.message}", e)
        }
    }

    suspend fun retryPendingBatches(): Int {
        Log.d(TAG, "Retrying pending call log batches...")

        val pendingBatches = callLogDao.getUnsyncedBatches()

        if (pendingBatches.isEmpty()) {
            Log.d(TAG, "No pending batches to retry")
            return 0
        }

        Log.d(TAG, "Found ${pendingBatches.size} pending batches")

        var successCount = 0

        for (entity in pendingBatches) {
            val batch = entityToBatch(entity)
            val uploadResult = callLogFirestoreDataSource.uploadCallLogBatch(batch)

            if (uploadResult.isSuccess) {
                callLogDao.markAsSynced(entity.batchId)
                markCallsAsSynced(batch)
                successCount++
                Log.d(TAG, "Retry succeeded for batch: ${entity.batchId}")
            } else {
                callLogDao.incrementRetryCount(entity.batchId)
                Log.w(TAG, "Retry failed for batch: ${entity.batchId}")
            }
        }

        callLogDao.deleteSyncedBatches()
        callLogDao.deleteFailedBatches(maxRetries = 5)

        Log.d(TAG, "Retry complete: $successCount/${pendingBatches.size} succeeded")

        return successCount
    }

    private fun entityToBatch(entity: PendingCallLogBatchEntity): CallLogBatch {
        val calls = CallLogTypeConverter.fromJson(entity.callsJson)

        return CallLogBatch(
            batchId = entity.batchId,
            userId = entity.userId,
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            batchDate = entity.batchDate,
            batchTimestamp = entity.batchTimestamp,
            calls = calls,
            isSynced = entity.isSynced
        )
    }

    fun observePendingBatchCount(): Flow<Int> {
        return callLogDao.observeUnsyncedCount()
    }

    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing all local call log data")
        callLogDao.deleteAllBatches()
        callLogDao.deleteAllSyncedLogs()
    }

    suspend fun cleanupOldSyncedLogs(keepDays: Int = 7) {
        val cutoffTimestamp = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
        callLogDao.deleteOldSyncedLogs(cutoffTimestamp)
        Log.d(TAG, "Cleaned up synced logs older than $keepDays days")
    }

    fun hasCallLogPermission(): Boolean {
        return callLogReader.hasCallLogPermission()
    }

    suspend fun getLastSyncInfo(): LastSyncInfo {
        val pendingCount = callLogDao.getUnsyncedBatches().size
        val totalSyncedCount = callLogDao.getTotalSyncedCount()

        return LastSyncInfo(
            pendingBatchCount = pendingCount,
            totalSyncedCallCount = totalSyncedCount
        )
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    data class LastSyncInfo(
        val pendingBatchCount: Int,
        val totalSyncedCallCount: Int
    )
}