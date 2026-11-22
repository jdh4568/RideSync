package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.runtime.Composable
import com.example.ridemate_api_test.feature.invitation.domain.model.InviteParticipant

@Composable
fun MemberSelectRoute(
    groupId: Long,
    selfUserId: Long,
    onBack: () -> Unit,
    onInvite: (List<InviteParticipant>) -> Unit,
    onNavigateLogin: () -> Unit,
    onOpenGroupSelect: () -> Unit,
) {
    MemberSelectScreen(
        groupId = groupId,
        selfUserId = selfUserId,
        onBack = onBack,
        onInvite = onInvite,
        onNavigateLogin = onNavigateLogin,
        onOpenGroupSelect = onOpenGroupSelect,
    )
}
