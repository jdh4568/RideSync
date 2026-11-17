package com.example.ridemate_api_test.feature.group.presentation.requests

import androidx.compose.runtime.Composable

@Composable
fun GroupJoinRequestsRoute(onDismiss: () -> Unit) {
    // Stage 1 has no API or mutation actions. State ownership is added with stage 2.
    GroupJoinRequestsDialog(GroupJoinRequestsUiState(), onDismiss)
}
