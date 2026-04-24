// ============================================================
// FILE: service/calllog/CallLogPermissionHelper.kt
// Purpose: Helper for checking and requesting call log permission
// ============================================================

package com.app.notisync_sender.service.calllog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogPermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val REQUIRED_PERMISSION = Manifest.permission.READ_CALL_LOG
    }

    fun getAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}