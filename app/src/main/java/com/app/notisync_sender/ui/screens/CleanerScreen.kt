package com.app.notisync_sender.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.notisync_sender.R
import com.app.notisync_sender.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

@Composable
fun CleanerScreen(
    onNavigateToDashBoard: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("cleaner_prefs", Context.MODE_PRIVATE) }

    // Secret access logic
    var clickCount by remember { mutableIntStateOf(0) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var inputPassword by remember { mutableStateOf("") }

    // Logic to determine initial value based on scheduled reset times
    val lastClickTime = sharedPref.getLong("last_click_time", 0L)
    val savedValue = sharedPref.getInt("saved_value", -1)
    val savedEndValue = sharedPref.getInt("saved_end_value", -1)

    // Scheduled reset times (hour, minute): 12am, 6am, 10am, 12pm, 3pm, 5pm, 6:47pm, 8pm, 11pm
    val resetTimes = listOf(
        0 to 0, 6 to 0, 10 to 0, 12 to 0, 15 to 0, 17 to 0, 18 to 47, 18 to 55, 20 to 0, 23 to 0
    )

    val now = Calendar.getInstance()
    val currentTimeMillis = now.timeInMillis

    var lastResetTimestamp = 0L
    for (time in resetTimes) {
        val resetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (resetCal.timeInMillis > currentTimeMillis) {
            resetCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (resetCal.timeInMillis > lastResetTimestamp) {
            lastResetTimestamp = resetCal.timeInMillis
        }
    }

    val isResetNeeded = lastClickTime < lastResetTimestamp || savedValue == -1 || savedEndValue == -1

    val initialValue = if (isResetNeeded) {
        Random.nextInt(70, 96)
    } else {
        savedValue
    }

    val endValue = if (isResetNeeded) {
        Random.nextInt(10, 19).also {
            with(sharedPref.edit()) {
                putInt("saved_end_value", it)
                apply()
            }
        }
    } else {
        savedEndValue
    }

    var progressValue by remember { mutableIntStateOf(initialValue) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F4F9))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top White Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f),
                shape = RoundedCornerShape(bottomStart = 50.dp, bottomEnd = 50.dp),
                color = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 48.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { /* Hidden Menu icon */ },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEEF3FF))
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color(0xFF007BFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Google Cleaner",
                            fontSize = 24.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                clickCount++
                                if (clickCount >= 5) {
                                    showReturnDialog = true
                                    clickCount = 0
                                }
                            }
                        )

                        // Profile Icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressSection(targetValue = progressValue)
                    Spacer(modifier = Modifier.weight(1.2f))
                }
            }

            // Bottom Grid Section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp)
                    .padding(top = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item { CategoryCard("System", Icons.Default.Settings, onClick = { Toast.makeText(context, "System Working Properly", Toast.LENGTH_LONG).show() }) }
                    item { CategoryCard("Battery", Icons.Default.Build, onClick = { Toast.makeText(context, "Battery health optimized!", Toast.LENGTH_LONG).show() }) }
                    item { CategoryCard("Data", Icons.Default.Info, onClick = { Toast.makeText(context, "Deep data scanning complete.", Toast.LENGTH_LONG).show() }) }
                    item { CategoryCard("Antivirus", Icons.Default.Lock, onClick = { Toast.makeText(context, "Your device is secured.", Toast.LENGTH_LONG).show() }) }
                }
            }
        }

        // Overlapping Smart Cleaning Button
        Button(
            onClick = {
                scope.launch {
                    val oldValue = progressValue
                    if (progressValue <= endValue) {
                        Toast.makeText(context, "System Already Cleaned!", Toast.LENGTH_LONG).show()
                    } else if (progressValue <= 30) {
                        Toast.makeText(context, "Optimizing System...", Toast.LENGTH_SHORT).show()
                        delay(800)
                        val reduction = Random.nextInt(1, 4)
                        progressValue = (progressValue - reduction).coerceAtLeast(endValue)
                        with(sharedPref.edit()) {
                            putLong("last_click_time", System.currentTimeMillis())
                            putInt("saved_value", progressValue)
                            apply()
                        }

                        Toast.makeText(
                            context,
                            "System Optimized! Cleaned ${oldValue - progressValue} more files",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val newValue = Random.nextInt(20, 31).coerceAtLeast(endValue + 1)
                        Toast.makeText(context, "System Cleaning...", Toast.LENGTH_SHORT).show()
                        progressValue = newValue
                        with(sharedPref.edit()) {
                            putLong("last_click_time", System.currentTimeMillis())
                            putInt("saved_value", newValue)
                            apply()
                        }
                        delay(3000)
                        Toast.makeText(context, "System Cleaned ${oldValue - newValue} files", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-15).dp)
                .height(64.dp)
                .fillMaxWidth(0.65f)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0xFF007BFF),
                    spotColor = Color(0xFF007BFF)
                ),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (progressValue > 85) Color(0xFFFF0000) else Color(0xFF007BFF)),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.rocket),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Smart Cleaning",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }

    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = {
                showReturnDialog = false
                inputPassword = ""
            },
            title = { Text("Admin Access", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your access password to return to the Dashboard.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.verifyAccessPassword(inputPassword)) {
                        viewModel.setCleanerAsStartDestination(false)
                        showReturnDialog = false
                        inputPassword = ""
                        onNavigateToDashBoard()
                    } else {
                        Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReturnDialog = false
                    inputPassword = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CircularProgressSection(targetValue: Int) {
    // Animate the integer value
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 1000),
        label = "ValueAnimation"
    )

    // Animate the progress float (0.0 to 1.0)
    val animatedProgress by animateFloatAsState(
        targetValue = targetValue / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "ProgressAnimation"
    )

    // Dynamic color: Red if value > 85, Blue otherwise
    val targetColor = if (animatedValue > 85) Color(0xFFFF4D4D) else Color(0xFF007BFF)
    val progressColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "ColorAnimation"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Dot(Modifier.align(Alignment.TopStart).offset(x = 45.dp, y = 35.dp), progressColor, 10.dp)
        Dot(Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 65.dp), progressColor, 14.dp)
        Dot(Modifier.align(Alignment.CenterStart).offset(x = 25.dp, y = (-30).dp), progressColor, 4.dp)
        Dot(Modifier.align(Alignment.BottomEnd).offset(x = (-40).dp, y = (-30).dp), progressColor.copy(alpha = 0.6f), 6.dp)
        Dot(Modifier.align(Alignment.BottomStart).offset(x = 40.dp, y = (-40).dp), progressColor.copy(alpha = 0.4f), 5.dp)

        Canvas(modifier = Modifier.size(210.dp)) {
            val strokeWidth = 16.dp.toPx()
            // Track (background arc)
            drawArc(
                color = Color(0xFFF0F4FF),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Progress arc using animated progress and color
            drawArc(
                color = progressColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedValue.toString(),
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Can be cleaned\nout $animatedValue file",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun Dot(modifier: Modifier, color: Color, size: Dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
    )
}

@Composable
fun CategoryCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize()
            ,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon with light blue circular background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEEF3FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF007BFF),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text labels
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Memory, Battery, Net",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
