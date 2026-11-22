package com.example.ridemate_api_test.feature.auth.presentation.signup

import androidx.compose.runtime.Composable

@Composable
fun SignupRoute(
    onBack: () -> Unit,
    onSignedUp: () -> Unit,
) {
    SignupScreen(onBack = onBack, onSignedUp = onSignedUp)
}
