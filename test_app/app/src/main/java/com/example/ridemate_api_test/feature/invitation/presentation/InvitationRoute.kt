package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.res.stringResource
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.feature.invitation.domain.model.InvitationNotice

@Composable
fun InvitationRoute(state: InvitationUiState, blockedByOtherModal: Boolean, onHide: () -> Unit,
    onRespond: (String, Boolean) -> Unit = { _, _ -> },
    onStart: (String) -> Unit = {}, onCancel: (String) -> Unit = {}, onDismissNotice: () -> Unit = {}) {
    // Shared by every authenticated destination; never opens a socket or performs a request.
    if (!blockedByOtherModal && !state.rideOpen) {
        if (state.notice != null) {
            HomeTheme { AlertDialog(onDismissRequest = onDismissNotice,
                text = { Text(stringResource(if (state.notice == InvitationNotice.CANCELLED)
                    R.string.invitation_cancelled_notice else R.string.invitation_start_ambiguous)) },
                confirmButton = { TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.invitation_notice_ok)) } }) }
        } else state.visiblePanel?.let { panel ->
            HomeTheme { InvitationDialog(panel, onHide = onHide,
                onAccept = { onRespond(panel.invitationId, true) },
                onReject = { onRespond(panel.invitationId, false) },
                onStart = { onStart(panel.invitationId) }, onCancel = { onCancel(panel.invitationId) }) }
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
