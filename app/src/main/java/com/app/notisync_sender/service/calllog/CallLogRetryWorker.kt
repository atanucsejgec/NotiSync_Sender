// ============================================================
// FILE: service/calllog/CallLogRetryWorker.kt
// Purpose: WorkManager worker that retries pending call log batches when online
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
class CallLogRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val callLogRepository: CallLogRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CallLogRetryWorker"
        private const val WORK_NAME = "call_log_retry_worker"

        fun scheduleOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CallLogRetryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "Call log retry worker scheduled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting call log retry...")

        return try {
            val retryCount = callLogRepository.retryPendingBatches()
            Log.d(TAG, "Call log retry complete — $retryCount batches uploaded")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Call log retry failed: ${e.message}", e)
            Result.retry()
        }
    }
}