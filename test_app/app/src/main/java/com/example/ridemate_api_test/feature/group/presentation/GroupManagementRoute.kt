package com.example.ridemate_api_test.feature.group.presentation

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
fun GroupManagementRoute(container: AppContainer, onBack: () -> Unit) {
    val model: GroupManagementViewModel = viewModel(factory = container.groupManagementViewModelFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.enter() }
    val leave = { model.reset(); onBack() }
    BackHandler(enabled = !state.showCreation) { leave() }
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
            if (activity?.isChangingConfigurations != true) model.reset()
        }
    }
    HomeTheme {
        GroupManagementScreen(state, leave, model::openCreation, model::dismissCreation,
            model::updateForm, onRefresh = model::refresh, onJoin = model::join, onCreate = model::create)
    }
}
