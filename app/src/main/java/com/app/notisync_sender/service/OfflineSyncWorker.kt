// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/service/OfflineSyncWorker.kt
// Purpose: WorkManager worker that retries uploading pending batches
// when internet becomes available
// ============================================================

package com.app.notisync_sender.service

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
import com.app.notisync_sender.data.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationRepository: NotificationRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "OfflineSyncWorker"
        private const val WORK_NAME = "offline_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<OfflineSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Offline sync worker scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Offline sync worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting offline sync...")

        return try {
            val uploadedCount = notificationRepository.retryPendingBatches()
            Log.d(TAG, "Offline sync complete — uploaded $uploadedCount batches")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Offline sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}