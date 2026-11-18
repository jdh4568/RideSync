package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme

// Fixtures never enter the application ViewModel or represent a server result.
@Preview(name = "Leader invitation", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LeaderInvitationPreview() {
    PreviewPanel(InvitationPanel.Leader("preview-leader", InvitationCounts(3, 1, 1, 1), canStart = true))
}

@Preview(name = "Member invitation", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MemberInvitationPreview() {
    PreviewPanel(InvitationPanel.Member("preview-member", "한강 모임", "홍길동"))
}

@Preview(name = "Invitation response waiting", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InvitationWaitingPreview() {
    PreviewPanel(InvitationPanel.Member("preview-waiting", "한강 모임", "홍길동", InvitationResponseUi.ACCEPT_SENT))
}

@Composable
private fun PreviewPanel(panel: InvitationPanel) {
    HomeTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFFB3B3B3))) {
            InvitationPlacement { InvitationContent(panel, onHide = {}, actionsAvailable = true) }
        }
    }
}
