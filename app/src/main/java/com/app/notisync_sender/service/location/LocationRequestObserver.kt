// ============================================================
// FILE: service/location/LocationRequestObserver.kt
// Purpose: Real-time observer for location requests using Firestore listener
// ============================================================

package com.app.notisync_sender.service.location

import android.util.Log
import com.app.notisync_sender.data.repository.LocationRepository
import com.app.notisync_sender.domain.model.LocationRequest
import com.app.notisync_sender.service.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRequestObserver @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationProvider: LocationProvider
) {

    companion object {
        private const val TAG = "LocationRequestObserver"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var observerJob: Job? = null
    private var isObserving = false

    private val processedRequestIds = mutableSetOf<String>()

    fun startObserving() {
        if (isObserving) {
            Log.d(TAG, "Already observing location requests")
            return
        }

        isObserving = true
        Log.d(TAG, "Starting location request observer")

        observerJob = scope.launch {
            locationRepository.observePendingRequests()
                .catch { e ->
                    Log.e(TAG, "Error observing location requests: ${e.message}", e)
                }
                .collectLatest { requests ->
                    handleLocationRequests(requests)
                }
        }
    }

    fun stopObserving() {
        Log.d(TAG, "Stopping location request observer")
        isObserving = false
        observerJob?.cancel()
        observerJob = null
        processedRequestIds.clear()
    }

    private suspend fun handleLocationRequests(requests: List<LocationRequest>) {
        val pendingRequests = requests.filter { request ->
            request.isPending() && !processedRequestIds.contains(request.requestId)
        }

        if (pendingRequests.isEmpty()) {
            return
        }

        Log.d(TAG, "Found ${pendingRequests.size} pending location requests")

        for (request in pendingRequests) {
            processedRequestIds.add(request.requestId)

            scope.launch {
                try {
                    Log.d(TAG, "Processing location request: ${request.requestId}")
                    val result = locationRepository.processLocationRequest(request)

                    if (result.isSuccess) {
                        Log.d(TAG, "Location request fulfilled: ${request.requestId}")
                    } else {
                        Log.e(TAG, "Failed to fulfill request: ${request.requestId}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing request ${request.requestId}: ${e.message}", e)
                }
            }
        }
    }

    fun isCurrentlyObserving(): Boolean = isObserving
}
