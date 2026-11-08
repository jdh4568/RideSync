package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.core.designsystem.theme.LoginTheme
import com.example.ridemate_api_test.core.di.AppContainer

@Composable
fun LoginRoute(container: AppContainer, onSignup: () -> Unit) {
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
    val viewModel: LoginViewModel = viewModel(factory = container.loginViewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginTheme {
        LoginScreen(state, viewModel::updateLoginId, viewModel::updatePassword,
            viewModel::submit, onSignup, viewModel::dismissNotice,
            viewModel::retryConnection, viewModel::cancelLogin)
    }
}
