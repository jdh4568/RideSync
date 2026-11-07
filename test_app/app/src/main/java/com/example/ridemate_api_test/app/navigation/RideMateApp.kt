package com.example.ridemate_api_test.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridemate_api_test.app.session.SessionConfirmationScreen
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginRoute

@Composable
fun RideMateApp(container: AppContainer) {
    val state by container.authRepository.state.collectAsStateWithLifecycle()
    // No authenticated back stack remains after logout/expiry.
    if (state.enteredSession) {
        SessionConfirmationScreen(state, container.authRepository::retryConnection, container.authRepository::logout)
    } else {
        LoginRoute(container)
    }
}
