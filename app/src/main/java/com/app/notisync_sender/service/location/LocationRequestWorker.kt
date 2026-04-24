// ============================================================
// FILE: service/location/LocationRequestWorker.kt
// Purpose: WorkManager worker that polls for location requests every 5 minutes
// ============================================================

package com.app.notisync_sender.service.location

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
import com.app.notisync_sender.data.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LocationRequestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationRepository: LocationRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LocationRequestWorker"
        private const val WORK_NAME = "location_request_poller"
        private const val POLL_INTERVAL_MINUTES = 5L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<LocationRequestWorker>(
                POLL_INTERVAL_MINUTES, TimeUnit.MINUTES
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

            Log.d(TAG, "Location request poller scheduled (every $POLL_INTERVAL_MINUTES minutes)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Location request poller cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Checking for pending location requests...")

        return try {
            locationRepository.checkAndProcessPendingRequests()

            locationRepository.retryPendingLocations()

            Log.d(TAG, "Location request check complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Location request check failed: ${e.message}", e)
            Result.retry()
        }
    }
}