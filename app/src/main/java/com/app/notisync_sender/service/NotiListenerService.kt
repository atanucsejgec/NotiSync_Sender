// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/service/NotiListenerService.kt
// Purpose: System-bound service that captures all notifications
// from other apps and forwards them to BatchProcessor
// ============================================================

package com.app.notisync_sender.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.app.notisync_sender.domain.model.CapturedNotification
import com.app.notisync_sender.domain.processor.BatchProcessor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * NotiListenerService — Captures all system notifications from other apps.
 */
@AndroidEntryPoint
class NotiListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotiListenerService"

        private val IGNORED_PACKAGES = setOf(
            "hjhyuy"
//            "android",
//            "com.android.systemui",
//            "com.android.settings",
//            "com.android.providers.downloads",
//            "com.app.notisync_sender",
//            "com.samsung.android.incallui",
//            "com.samsung.android.dialer",
//            "com.miui.home",
//            "com.huawei.systemmanager"
        )

        private val IGNORED_CATEGORIES = setOf(
            "rtre"
//            Notification.CATEGORY_TRANSPORT,
//            Notification.CATEGORY_SERVICE,
//            Notification.CATEGORY_SYSTEM,
//            Notification.CATEGORY_CALL,
//            Notification.CATEGORY_NAVIGATION
        )
    }

    @Inject
    lateinit var batchProcessor: BatchProcessor

    private val packageManagerInstance: PackageManager by lazy {
        applicationContext.packageManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            val packageName = sbn.packageName
            val notification = sbn.notification

            /* 1. Filter by Package */
            if (shouldIgnorePackage(packageName)) return

            /* 2. Filter by Category */
            if (shouldIgnoreCategory(notification.category)) return

            /* 3. Filter our own Foreground Service Notification explicitly */
            // Checking for the flag is often more reliable than just category
            if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 && 
                packageName == "com.app.notisync_sender") {
                return
            }

            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val message = extractMessage(extras)

            if (title.isEmpty() && message.isEmpty()) return

            val appName = getAppName(packageName)
            val capturedNotification = CapturedNotification.create(
                appName = appName,
                title = title,
                message = message,
                timestamp = System.currentTimeMillis()
            )

            batchProcessor.addNotification(capturedNotification)
            Log.d(TAG, "Captured: $appName — $title")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
        return IGNORED_PACKAGES.contains(packageName) || packageName.startsWith("com.androidx.")
    }

    private fun shouldIgnoreCategory(category: String?): Boolean {
        if (category == null) return false
        return IGNORED_CATEGORIES.contains(category)
    }

    private fun extractMessage(extras: android.os.Bundle): String {
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.let {
            if (it.isNotEmpty()) return it
        }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.let {
            if (it.isNotEmpty()) return it
        }
        return ""
    }

    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManagerInstance.getApplicationInfo(packageName, 0)
            packageManagerInstance.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
    }
}
