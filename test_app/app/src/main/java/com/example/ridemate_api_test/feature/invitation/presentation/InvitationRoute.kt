package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme

@Composable
fun InvitationRoute(state: InvitationUiState, blockedByOtherModal: Boolean, onHide: () -> Unit) {
    // Shared by every authenticated destination; never opens a socket or performs a request.
    if (!blockedByOtherModal && !state.rideOpen) {
        state.visiblePanel?.let { panel ->
            HomeTheme { InvitationDialog(panel, onHide = onHide) }
        }
    }
}

@Composable
fun InvitationHomeActions(state: InvitationUiState, onReopen: () -> Unit, onOpenRide: () -> Unit) {
    Column {
        TextButton(onClick = onReopen, enabled = state.hasInvitations) {
            Text(stringResource(R.string.invitation_reopen))
        }
        if (state.ride != null) {
            TextButton(onClick = onOpenRide) { Text(stringResource(R.string.ride_reopen)) }
        }
    }
}
