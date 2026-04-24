// ============================================================
// FILE: service/location/LocationSyncWorker.kt
// Purpose: WorkManager worker that syncs pending locations when online
// ============================================================

package com.app.notisync_sender.service.location

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
import com.app.notisync_sender.data.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LocationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationRepository: LocationRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LocationSyncWorker"
        private const val WORK_NAME = "location_sync_worker"

        fun scheduleOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<LocationSyncWorker>()
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

            Log.d(TAG, "Location sync worker scheduled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting location sync...")

        return try {
            val uploadedCount = locationRepository.retryPendingLocations()
            Log.d(TAG, "Location sync complete — uploaded $uploadedCount locations")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Location sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}