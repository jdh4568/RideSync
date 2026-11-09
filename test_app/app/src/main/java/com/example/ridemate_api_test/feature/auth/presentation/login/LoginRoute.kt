package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridemate_api_test.core.designsystem.theme.LoginTheme

@Composable
fun LoginRoute(viewModel: LoginViewModel, onSignup: () -> Unit) {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            previousStatus?.let { controller.isAppearanceLightStatusBars = it }
            previousNavigation?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginTheme {
        LoginScreen(state, viewModel::updateLoginId, viewModel::updatePassword,
            viewModel::submit, onSignup, viewModel::dismissNotice,
            viewModel::retryConnection, viewModel::cancelLogin)
    }
}
