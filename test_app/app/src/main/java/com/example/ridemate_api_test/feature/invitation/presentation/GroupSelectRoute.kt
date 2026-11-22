package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.runtime.Composable

@Composable
fun GroupSelectRoute(
    onBack: () -> Unit,
    onNext: (Long) -> Unit,
    onNavigateLogin: () -> Unit,
) {
    GroupSelectScreen(
        onBack = onBack,
        onNext = onNext,
        onNavigateLogin = onNavigateLogin,
    )
}
