// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/ui/navigation/SenderNavGraph.kt
// Purpose: Defines all navigation routes and screen transitions
// ============================================================

package com.app.notisync_sender.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.notisync_sender.data.repository.AuthState
import com.app.notisync_sender.ui.screens.DashboardScreen
import com.app.notisync_sender.ui.screens.LoginScreen
import com.app.notisync_sender.ui.screens.SettingsScreen
import com.app.notisync_sender.ui.screens.CleanerScreen
import com.app.notisync_sender.viewmodel.AuthViewModel
import com.app.notisync_sender.viewmodel.DashboardViewModel

object SenderRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val CLEANER = "cleaner"
}

@Composable
fun SenderNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    onRequestLocationPermission: () -> Unit = {},
    onRequestCallLogPermission: () -> Unit = {}
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val useCleanerAsStart by dashboardViewModel.useCleanerAsStart.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                if (navController.currentDestination?.route == SenderRoutes.LOGIN) {
                    val dest = if (useCleanerAsStart) SenderRoutes.CLEANER else SenderRoutes.DASHBOARD
                    navController.navigate(dest) {
                        popUpTo(SenderRoutes.LOGIN) { inclusive = true }
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                if (navController.currentDestination?.route != SenderRoutes.LOGIN) {
                    navController.navigate(SenderRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    val startDestination = when {
        authState is AuthState.Authenticated && useCleanerAsStart -> SenderRoutes.CLEANER
        authState is AuthState.Authenticated -> SenderRoutes.DASHBOARD
        else -> SenderRoutes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        }
    ) {
        composable(SenderRoutes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel
            )
        }

        composable(SenderRoutes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToSettings = {
                    navController.navigate(SenderRoutes.SETTINGS)
                },
                onNavigateToCleaner = {
                    navController.navigate(SenderRoutes.CLEANER)
                },
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestCallLogPermission = onRequestCallLogPermission
            )
        }

        composable(SenderRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(SenderRoutes.CLEANER) {
            CleanerScreen(
                onNavigateToDashBoard = {
                    navController.navigate(SenderRoutes.DASHBOARD) {
                        // Clear backstack to prevent going back to locked cleaner
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
