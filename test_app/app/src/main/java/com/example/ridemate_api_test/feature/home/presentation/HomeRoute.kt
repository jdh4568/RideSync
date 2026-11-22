package com.example.ridemate_api_test.feature.home.presentation

import androidx.compose.runtime.Composable

@Composable
fun HomeRoute(
    onOpenGroupList: () -> Unit,
    onStartRide: () -> Unit,
    onAcceptInvite: () -> Unit,
) {
    HomeScreen(
        onOpenGroupList = onOpenGroupList,
        onStartRide = onStartRide,
        onAcceptInvite = onAcceptInvite,
    )
}
