package com.app.notisync_sender

// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/NotiSyncSenderApp.kt
// Purpose: Hilt Application class — entry point for dependency injection
// Also configures WorkManager to use HiltWorkerFactory
// ============================================================



import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * NotiSyncSenderApp — Application class for the Sender App.
 *
 * What: Serves as the root of the dependency injection graph and
 *       configures WorkManager to use Hilt-injected workers.
 *
 * Why: @HiltAndroidApp triggers Hilt code generation at compile time,
 *      creating a base class that all Hilt-injected components depend on.
 *      Implementing Configuration.Provider allows WorkManager to use
 *      @HiltWorker annotated workers with constructor injection.
 */
@HiltAndroidApp
class NotiSyncSenderApp : Application(), Configuration.Provider {

    /**
     * HiltWorkerFactory is injected by Hilt — it creates Worker instances
     * with proper dependency injection support.
     * This is required because WorkManager normally creates workers via
     * reflection, which doesn't support @Inject constructor parameters.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Called when the application is first created.
     * Creates the notification channel required for the foreground service.
     * On Android 8.0+ (API 26+), notification channels are mandatory.
     */
    override fun onCreate() {
        super.onCreate()

        /* Create the notification channel for the foreground sync service */
        createSyncNotificationChannel()
    }

    /**
     * Provides WorkManager configuration with HiltWorkerFactory.
     * This replaces the default WorkManager initialization that was
     * disabled in AndroidManifest.xml via the InitializationProvider override.
     *
     * Why: Default WorkManager uses reflection to create workers.
     *      HiltWorkerFactory enables constructor injection in workers,
     *      allowing us to inject repositories and other dependencies.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            /* Use Hilt's worker factory instead of default reflection-based factory */
            .setWorkerFactory(workerFactory)
            /* Set minimum logging level for WorkManager internal logs */
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * Creates the notification channel for the persistent foreground service.
     *
     * What: Registers a notification channel with Android's notification system.
     *
     * Why: Android 8.0+ requires all notifications to be assigned to a channel.
     *      The foreground service notification ("Notification Sync Active")
     *      must use this channel. LOW importance means no sound or vibration
     *      — appropriate for a persistent background indicator.
     */
    private fun createSyncNotificationChannel() {
        /* Notification channels are only available on Android 8.0+ (API 26+) */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /* Channel ID — referenced when building the foreground notification */
            val channelId = SYNC_CHANNEL_ID

            /* Channel name — visible to user in app notification settings */
            val channelName = "Notification Sync Service"

            /* Channel description — explains purpose to user */
            val channelDescription = "Keeps the notification sync service running"

            /* LOW importance — no sound, no vibration, just a persistent indicator */
            val importance = NotificationManager.IMPORTANCE_MIN

            /* Create the channel object with ID, name, and importance */
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }

            /* Register the channel with the system notification manager */
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Companion object holds constants used across the app.
     * SYNC_CHANNEL_ID is referenced by SyncForegroundService when
     * building the persistent notification.
     */
    companion object {
        /* Notification channel ID for the foreground sync service */
        const val SYNC_CHANNEL_ID = "notisync_foreground_channel"

        /* Notification ID for the foreground service — must be unique and > 0 */
        const val SYNC_NOTIFICATION_ID = 1001
    }
}