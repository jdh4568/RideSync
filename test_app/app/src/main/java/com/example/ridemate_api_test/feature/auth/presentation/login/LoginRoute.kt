package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.compose.runtime.Composable

@Composable
fun LoginRoute(
    onNavigateSignup: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    LoginScreen(
        onNavigateSignup = onNavigateSignup,
        onNavigateHome = onNavigateHome,
    )
}
