// ============================================================
// FILE: service/calllog/CallLogSyncWorker.kt
// Purpose: WorkManager worker that syncs call logs daily at 11PM
// ============================================================

package com.app.notisync_sender.service.calllog

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.app.notisync_sender.data.repository.CallLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class CallLogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val callLogRepository: CallLogRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CallLogSyncWorker"
        private const val WORK_NAME = "daily_call_log_sync"
        private const val TARGET_HOUR = 23
        private const val TARGET_MINUTE = 0

        fun schedule(context: Context) {
            val initialDelay = calculateInitialDelay()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CallLogSyncWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "Call log sync worker scheduled (daily at $TARGET_HOUR:${TARGET_MINUTE.toString().padStart(2, '0')})")
            Log.d(TAG, "Initial delay: ${initialDelay / 1000 / 60} minutes")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Call log sync worker cancelled")
        }

        private fun calculateInitialDelay(): Long {
            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
                set(Calendar.MINUTE, TARGET_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (targetTime.before(now) || targetTime == now) {
                targetTime.add(Calendar.DAY_OF_MONTH, 1)
            }

            return targetTime.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily call log sync...")

        return try {
            if (!callLogRepository.hasCallLogPermission()) {
                Log.e(TAG, "Call log permission not granted — skipping sync")
                return Result.success()
            }

            val syncResult = callLogRepository.syncDailyCallLogs()

            syncResult.onSuccess { count ->
                Log.d(TAG, "Daily sync complete — synced $count calls")
            }.onFailure { error ->
                Log.e(TAG, "Sync failed: ${error.message}")
            }

            val retryCount = callLogRepository.retryPendingBatches()
            Log.d(TAG, "Retried pending batches — $retryCount succeeded")

            callLogRepository.cleanupOldSyncedLogs(keepDays = 7)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Call log sync worker failed: ${e.message}", e)
            Result.retry()
        }
    }
}