// ============================================================
// FILE: data/repository/LocationRepository.kt
// Purpose: Orchestrates location operations with offline support
// ============================================================

package com.app.notisync_sender.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.app.notisync_sender.data.local.LocationDao
import com.app.notisync_sender.data.local.PendingLocationEntity
import com.app.notisync_sender.data.local.ProcessedLocationRequestEntity
import com.app.notisync_sender.data.remote.LocationFirestoreDataSource
import com.app.notisync_sender.domain.model.DeviceLocation
import com.app.notisync_sender.domain.model.DeviceInfo
import com.app.notisync_sender.domain.model.LocationRequest
import com.app.notisync_sender.service.location.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationFirestoreDataSource: LocationFirestoreDataSource,
    private val locationDao: LocationDao,
    private val locationProvider: LocationProvider,
    private val authRepository: AuthRepository,
    private val deviceInfo: DeviceInfo
) {

    companion object {
        private const val TAG = "LocationRepository"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun fetchAndUploadLocation(requestId: String? = null): Result<DeviceLocation> {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Log.e(TAG, "No user logged in")
            return Result.failure(Exception("User not logged in"))
        }

        val locationResult = locationProvider.getCurrentLocation(userId, requestId)

        if (locationResult.isFailure) {
            return locationResult
        }

        val location = locationResult.getOrThrow()

        if (isNetworkAvailable()) {
            val uploadResult = locationFirestoreDataSource.uploadLocation(location)

            if (uploadResult.isSuccess) {
                Log.d(TAG, "Location uploaded successfully")

                if (requestId != null) {
                    markRequestAsFulfilled(requestId)
                    markRequestAsProcessed(requestId)
                }

                return Result.success(location)
            } else {
                Log.w(TAG, "Upload failed — saving to local queue")
                saveLocationToLocalQueue(location)
                return Result.success(location)
            }
        } else {
            Log.d(TAG, "Offline — saving location to local queue")
            saveLocationToLocalQueue(location)
            return Result.success(location)
        }
    }

    suspend fun processLocationRequest(request: LocationRequest): Result<DeviceLocation> {
        if (isRequestProcessed(request.requestId)) {
            Log.d(TAG, "Request already processed: ${request.requestId}")
            return Result.failure(Exception("Request already processed"))
        }

        if (request.isExpired()) {
            Log.d(TAG, "Request expired: ${request.requestId}")
            markRequestAsProcessed(request.requestId)
            return Result.failure(Exception("Request expired"))
        }

        return fetchAndUploadLocation(request.requestId)
    }

    suspend fun checkAndProcessPendingRequests() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Log.e(TAG, "No user logged in")
            return
        }

        val result = locationFirestoreDataSource.getPendingLocationRequests(
            userId = userId,
            deviceId = deviceInfo.deviceId
        )

        result.onSuccess { requests ->
            val pendingRequests = requests.filter { it.isPending() }
            Log.d(TAG, "Found ${pendingRequests.size} pending location requests")

            for (request in pendingRequests) {
                if (!isRequestProcessed(request.requestId)) {
                    Log.d(TAG, "Processing location request: ${request.requestId}")
                    processLocationRequest(request)
                }
            }
        }
    }

    fun observePendingRequests(): Flow<List<LocationRequest>> {
        val userId = authRepository.getCurrentUserId() ?: return kotlinx.coroutines.flow.emptyFlow()

        return locationFirestoreDataSource.observePendingLocationRequests(
            userId = userId,
            deviceId = deviceInfo.deviceId
        )
    }

    suspend fun retryPendingLocations(): Int {
        Log.d(TAG, "Retrying pending location uploads...")

        val pendingLocations = locationDao.getUnsyncedLocations()

        if (pendingLocations.isEmpty()) {
            Log.d(TAG, "No pending locations to retry")
            return 0
        }

        Log.d(TAG, "Found ${pendingLocations.size} pending locations")

        var successCount = 0

        for (entity in pendingLocations) {
            val location = entity.toDeviceLocation()
            val uploadResult = locationFirestoreDataSource.uploadLocation(location)

            if (uploadResult.isSuccess) {
                locationDao.markAsSynced(entity.locationId)
                successCount++
                Log.d(TAG, "Retry succeeded for location: ${entity.locationId}")

                entity.requestId?.let { requestId ->
                    markRequestAsFulfilled(requestId)
                }
            } else {
                locationDao.incrementRetryCount(entity.locationId)
                Log.w(TAG, "Retry failed for location: ${entity.locationId}")
            }
        }

        locationDao.deleteSyncedLocations()
        locationDao.deleteFailedLocations(maxRetries = 5)

        Log.d(TAG, "Retry complete: $successCount/${pendingLocations.size} succeeded")

        return successCount
    }

    private suspend fun saveLocationToLocalQueue(location: DeviceLocation) {
        try {
            val entity = location.toPendingEntity()
            locationDao.insertPendingLocation(entity)
            Log.d(TAG, "Location saved to local queue: ${location.locationId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save location to local queue: ${e.message}", e)
        }
    }

    private suspend fun markRequestAsFulfilled(requestId: String) {
        val userId = authRepository.getCurrentUserId() ?: return

        locationFirestoreDataSource.markRequestAsFulfilled(
            userId = userId,
            deviceId = deviceInfo.deviceId,
            requestId = requestId
        )
    }

    private suspend fun markRequestAsProcessed(requestId: String) {
        try {
            locationDao.insertProcessedRequest(
                ProcessedLocationRequestEntity(
                    requestId = requestId,
                    processedAt = System.currentTimeMillis(),
                    wasSuccessful = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark request as processed: ${e.message}")
        }
    }

    private suspend fun isRequestProcessed(requestId: String): Boolean {
        return try {
            locationDao.isRequestProcessed(requestId)
        } catch (e: Exception) {
            false
        }
    }

    fun observePendingLocationCount(): Flow<Int> {
        return locationDao.observeUnsyncedCount()
    }

    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing all local location data")
        locationDao.deleteAllLocations()
        locationDao.deleteAllProcessedRequests()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun DeviceLocation.toPendingEntity(): PendingLocationEntity {
        return PendingLocationEntity(
            locationId = this.locationId,
            userId = this.userId,
            deviceId = this.deviceId,
            deviceName = this.deviceName,
            latitude = this.latitude,
            longitude = this.longitude,
            accuracy = this.accuracy,
            timestamp = this.timestamp,
            provider = this.provider.name.lowercase(),
            requestId = this.requestId,
            isSynced = false,
            retryCount = 0
        )
    }

    private fun PendingLocationEntity.toDeviceLocation(): DeviceLocation {
        val provider = when (this.provider.lowercase()) {
            "gps" -> DeviceLocation.LocationProvider.GPS
            "network" -> DeviceLocation.LocationProvider.NETWORK
            "fused" -> DeviceLocation.LocationProvider.FUSED
            "ip" -> DeviceLocation.LocationProvider.IP
            else -> DeviceLocation.LocationProvider.UNKNOWN
        }

        return DeviceLocation(
            locationId = this.locationId,
            userId = this.userId,
            deviceId = this.deviceId,
            deviceName = this.deviceName,
            latitude = this.latitude,
            longitude = this.longitude,
            accuracy = this.accuracy,
            timestamp = this.timestamp,
            provider = provider,
            requestId = this.requestId
        )
    }
}
