package com.example.ridemate_api_test.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme

/** Not a navigation destination yet. Stage 2 supplies the app-owned model and session callbacks. */
@Composable
fun HomeRoute(model: HomeViewModel, onRetryConnection: () -> Unit, onLogout: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    HomeTheme {
        HomeScreen(state, model::showNotice, model::dismissNotice, onRetryConnection, onLogout)
    }
}
