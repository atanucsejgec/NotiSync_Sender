// ============================================================
// FILE: service/calllog/CallLogManualSyncWorker.kt
// Purpose: WorkManager worker for manual call log sync trigger
// ============================================================

package com.app.notisync_sender.service.calllog

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.app.notisync_sender.data.repository.CallLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CallLogManualSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val callLogRepository: CallLogRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CallLogManualSyncWorker"
        private const val WORK_NAME = "call_log_manual_sync"

        fun scheduleNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CallLogManualSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "Manual call log sync scheduled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting manual call log sync...")

        return try {
            if (!callLogRepository.hasCallLogPermission()) {
                Log.e(TAG, "Call log permission not granted")
                return Result.failure()
            }

            val syncResult = callLogRepository.syncDailyCallLogs()

            syncResult.onSuccess { count ->
                Log.d(TAG, "Manual sync complete — synced $count calls")
            }.onFailure { error ->
                Log.e(TAG, "Manual sync failed: ${error.message}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Manual call log sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}