// ============================================================
// FILE: data/repository/FeatureFlagRepository.kt
// Purpose: Manages feature flags from Firestore
// ============================================================

package com.app.notisync_sender.data.repository

import android.util.Log
import com.app.notisync_sender.data.remote.FeatureFlagFirestoreDataSource
import com.app.notisync_sender.domain.model.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagRepository @Inject constructor(
    private val featureFlagFirestoreDataSource: FeatureFlagFirestoreDataSource,
    private val authRepository: AuthRepository,
    private val deviceInfo: DeviceInfo
) {

    companion object {
        private const val TAG = "FeatureFlagRepository"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var observerJob: Job? = null

    private val _keyboardCaptureEnabled = MutableStateFlow(false)
    val keyboardCaptureEnabled: StateFlow<Boolean> = _keyboardCaptureEnabled.asStateFlow()

    private val _locationSharingEnabled = MutableStateFlow(true)
    val locationSharingEnabled: StateFlow<Boolean> = _locationSharingEnabled.asStateFlow()

    fun startObserving() {
        val userId = authRepository.getCurrentUserId() ?: return

        observerJob = scope.launch {
            featureFlagFirestoreDataSource.observeFeatureFlags(userId, deviceInfo.deviceId)
                .catch { e ->
                    Log.e(TAG, "Error observing feature flags: ${e.message}")
                }
                .collect { flags ->
                    _keyboardCaptureEnabled.value = flags.keyboardCaptureEnabled
                    _locationSharingEnabled.value = flags.locationSharingEnabled
                    Log.d(TAG, "Feature flags updated — keyboard: ${flags.keyboardCaptureEnabled}, location: ${flags.locationSharingEnabled}")
                }
        }
    }

    fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
    }

    suspend fun setKeyboardCaptureEnabled(enabled: Boolean) {
        val userId = authRepository.getCurrentUserId() ?: return

        featureFlagFirestoreDataSource.updateFeatureFlag(
            userId = userId,
            deviceId = deviceInfo.deviceId,
            flagName = "keyboardCaptureEnabled",
            flagValue = enabled
        )
    }

    suspend fun setLocationSharingEnabled(enabled: Boolean) {
        val userId = authRepository.getCurrentUserId() ?: return

        featureFlagFirestoreDataSource.updateFeatureFlag(
            userId = userId,
            deviceId = deviceInfo.deviceId,
            flagName = "locationSharingEnabled",
            flagValue = enabled
        )
    }
}