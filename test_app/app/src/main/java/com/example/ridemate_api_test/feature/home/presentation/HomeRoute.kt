package com.example.ridemate_api_test.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.group.presentation.requests.GroupJoinRequestsRoute

@Composable
fun HomeRoute(container: AppContainer, onOpenGroups: () -> Unit) {
    val model: HomeViewModel = viewModel(factory = container.homeViewModelFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
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
    HomeTheme {
        HomeScreen(state, { notice ->
            if (notice == HomeNotice.GROUPS) onOpenGroups() else model.showNotice(notice)
        }, model::dismissNotice, model::retryConnection, model::logout)
        GroupJoinRequestsRoute(container, visible = state.notice == HomeNotice.NOTIFICATIONS,
            onDismiss = model::dismissNotice)
    }
}
