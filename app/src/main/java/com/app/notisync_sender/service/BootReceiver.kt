// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/service/BootReceiver.kt
// Purpose: Restarts foreground service after device reboot
// ============================================================

package com.app.notisync_sender.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
// (1) NEW: Import LocationRequestWorker
import com.app.notisync_sender.service.location.LocationRequestWorker

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed — starting SyncForegroundService")

            val serviceIntent = Intent(context, SyncForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // (2) NEW: Schedule location request worker after boot
            LocationRequestWorker.schedule(context)
            Log.d(TAG, "Location request worker scheduled after boot")
        }
    }
}