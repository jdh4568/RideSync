package com.example.ridemate_api_test.feature.group.presentation.selection

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.core.di.AppContainer

@Composable
fun GroupSelectionRoute(container: AppContainer, onBack: () -> Unit) {
    val model: GroupSelectionViewModel = viewModel(factory = container.groupSelectionViewModelFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.enter() }
    val leave = { model.reset(); onBack() }
    BackHandler(enabled = !state.showDeveloping) { leave() }
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view, model) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            status?.let { controller.isAppearanceLightStatusBars = it }
            navigation?.let { controller.isAppearanceLightNavigationBars = it }
            if (activity?.isChangingConfigurations != true) model.reset()
        }
    }
    HomeTheme {
        GroupSelectionScreen(state, leave, model::select, model::complete, model::dismissDeveloping,
            onRetry = model::enter)
    }
}
