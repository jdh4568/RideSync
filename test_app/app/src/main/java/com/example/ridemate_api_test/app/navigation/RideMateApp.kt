package com.example.ridemate_api_test.app.navigation

import androidx.compose.runtime.Composable
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.connection.presentation.ConnectionRoute

@Composable
fun RideMateApp(container: AppContainer) {
    ConnectionRoute(container.connectionRepository)
}
