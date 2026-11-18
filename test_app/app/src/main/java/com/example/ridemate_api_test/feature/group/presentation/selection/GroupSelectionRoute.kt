package com.example.ridemate_api_test.feature.group.presentation.selection

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme

@Composable
fun GroupSelectionRoute(model: GroupSelectionViewModel, listState: LazyListState,
    onBack: () -> Unit, onOpenMembers: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.enter() }
    val leave = { model.reset(); onBack() }
    BackHandler { leave() }
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
            // The parent ride-selection flow owns reset; member navigation keeps this state.
        }
    }
    HomeTheme {
        GroupSelectionScreen(state, leave, model::select, onOpenMembers, onRetry = model::retry,
            listState = listState)
    }
}
