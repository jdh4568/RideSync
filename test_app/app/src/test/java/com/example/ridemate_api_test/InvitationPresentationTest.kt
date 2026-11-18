package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.invitation.presentation.*
import com.example.ridemate_api_test.feature.ride.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class InvitationPresentationTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(1, "사용자"), enteredSession = true))
        var networkCommands = 0
        override fun login(loginId: String, password: String) { networkCommands++ }
        override fun retryConnection() { networkCommands++ }
        override fun logout() { networkCommands++ }
    }

    private val first = InvitationPanel.Member("first", "한강 모임", "리더")
    private val second = InvitationPanel.Leader("second", InvitationCounts(3, 1, 1, 1), canStart = true)
    private val ride = RideDestination(10, 2, RideRole.MEMBER)

    private fun scenario(block: (Auth, InvitationViewModel) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val auth = Auth()
            val model = InvitationViewModel(auth, scope, InvitationUiState(listOf(first, second), ride = ride))
            block(auth, model)
        } finally { scope.cancel() }
    }

    @Test fun hideAndReopenDoNotRespondCancelOrReplaceTheInvitation() = scenario { auth, model ->
        assertEquals(first, model.uiState.value.visiblePanel)
        model.hidePanel()
        assertEquals(second, model.uiState.value.visiblePanel)
        model.hidePanel()
        assertNull(model.uiState.value.visiblePanel)
        assertTrue(model.uiState.value.hasInvitations)
        model.reopen()
        assertEquals(first, model.uiState.value.visiblePanel)
        assertEquals(listOf(first, second), model.uiState.value.panels)
        assertEquals(0, auth.networkCommands)
    }

    @Test fun leavingRidePreservesTheSameSessionAndRoleWithoutFinishingIt() = scenario { auth, model ->
        model.openRide()
        assertTrue(model.uiState.value.rideOpen)
        model.backHome()
        assertFalse(model.uiState.value.rideOpen)
        assertEquals(ride, model.uiState.value.ride)
        model.openRide()
        assertEquals(10L, model.uiState.value.ride?.rideSessionId)
        assertEquals(RideRole.MEMBER, model.uiState.value.ride?.role)
        assertEquals(0, auth.networkCommands)
    }

    @Test fun disconnectAndReconnectDoNotInventARejectionOrCancellation() = scenario { auth, model ->
        model.hidePanel()
        val previous = model.uiState.value
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        assertEquals(previous, model.uiState.value)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        assertEquals(previous, model.uiState.value)
        assertEquals(0, auth.networkCommands)
    }

    @Test fun accountChangeAndLogoutClearPanelsHiddenStateAndRide() = scenario { auth, model ->
        model.hidePanel()
        model.openRide()
        auth.state.value = auth.state.value.copy(user = AuthUser(2, "다른 사용자"))
        assertEquals(InvitationUiState(), model.uiState.value)
        auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        model.reopen()
        model.openRide()
        assertEquals(InvitationUiState(), model.uiState.value)
        assertEquals(0, auth.networkCommands)
    }

    @Test fun normalConstructionHasNoSamplesAndCannotEnterARide() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val auth = Auth()
            val model = InvitationViewModel(auth, scope)
            model.openRide()
            model.reopen()
            assertEquals(InvitationUiState(), model.uiState.value)
            assertEquals(0, auth.networkCommands)
        } finally { scope.cancel() }
    }

    @Test fun unknownLeaderCountsStayUnknownAndInvalidCountTotalsAreRejected() {
        assertNull(InvitationPanel.Leader("unknown").counts)
        assertFalse(InvitationPanel.Leader("unknown").canStart)
        assertThrows(IllegalArgumentException::class.java) { InvitationCounts(3, 2, 2, 0) }
        assertThrows(IllegalArgumentException::class.java) { RideDestination(0, 1, RideRole.LEADER) }
    }
}
