package com.example.ridemate_api_test.feature.group.presentation.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.core.di.AppContainer

@Composable
fun GroupJoinRequestsRoute(container: AppContainer, visible: Boolean, onDismiss: () -> Unit) {
    val model: GroupJoinRequestsViewModel = viewModel(factory = container.groupJoinRequestsViewModelFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(visible) { if (visible) model.open() else model.close() }
    DisposableEffect(model) { onDispose { model.close() } }
    if (visible && state.visible) {
        GroupJoinRequestsDialog(state, { model.close(); onDismiss() }, model::refresh, model::respond)
    }
}
