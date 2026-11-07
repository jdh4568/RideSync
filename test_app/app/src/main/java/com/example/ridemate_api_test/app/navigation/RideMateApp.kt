package com.example.ridemate_api_test.app.navigation

import androidx.compose.runtime.Composable
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginRoute

@Composable
fun RideMateApp(container: AppContainer) {
    LoginRoute(container)
}
