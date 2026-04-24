// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/MainActivity.kt
// Purpose: Single Activity hosting all Compose screens
// with runtime permission handling for Location and Call Log
// ============================================================

package com.app.notisync_sender

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.app.notisync_sender.ui.navigation.SenderNavGraph
import com.app.notisync_sender.ui.theme.NotisyncsenderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Runtime permission launcher for location — shows system dialog
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Results handled automatically — ViewModel polling detects the change
    }

    // Runtime permission launcher for call log — shows system dialog
    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Result handled automatically — ViewModel polling detects the change
    }

    // Runtime permission launcher for notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Result handled automatically
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+ at startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            NotisyncsenderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SenderAppContent(
                        modifier = Modifier.padding(innerPadding),
                        onRequestLocationPermission = ::requestLocationPermission,
                        onRequestCallLogPermission = ::requestCallLogPermission
                    )
                }
            }
        }
    }

    // Called from DashboardScreen when user taps "Enable" on Location card
    fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Called from DashboardScreen when user taps "Enable" on Call Log card
    fun requestCallLogPermission() {
        callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
    }
}

// Root composable that passes permission callbacks down to NavGraph
@Composable
fun SenderAppContent(
    modifier: Modifier = Modifier,
    onRequestLocationPermission: () -> Unit = {},
    onRequestCallLogPermission: () -> Unit = {}
) {
    SenderNavGraph(
        modifier = modifier,
        onRequestLocationPermission = onRequestLocationPermission,
        onRequestCallLogPermission = onRequestCallLogPermission
    )
}
