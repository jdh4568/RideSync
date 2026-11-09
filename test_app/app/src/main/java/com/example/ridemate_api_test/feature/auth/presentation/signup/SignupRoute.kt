package com.example.ridemate_api_test.feature.auth.presentation.signup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.core.designsystem.theme.LoginTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignupRoute(container: AppContainer, onBack: () -> Unit, onSuccess: (String) -> Unit) {
    val model: SignupViewModel = viewModel(factory = container.signupViewModelFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val imeVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val leave = { if (!model.uiState.value.isSubmitting) { model.reset(); onBack() } }
    BackHandler { if (imeVisible) keyboard?.hide() else leave() }
    LaunchedEffect(state.completedLoginId) {
        state.completedLoginId?.let { loginId ->
            onSuccess(loginId)
            model.reset()
        }
    }
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            status?.let { controller.isAppearanceLightStatusBars = it }
            navigation?.let { controller.isAppearanceLightNavigationBars = it }
            // Preserve the in-memory form across Activity recreation only.
            if (activity?.isChangingConfigurations != true) model.reset()
        }
    }
    LoginTheme {
        SignupScreen(state, model::update, model::checkAvailability, model::submit,
            leave)
    }
}

