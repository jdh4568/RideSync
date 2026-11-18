package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.presentation.members.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class MemberSelectionViewModelTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true))
        override fun login(loginId: String, password: String) = error("No authentication command")
        override fun retryConnection() = error("Screen must not reconnect the app socket")
        override fun logout() { state.value = AuthState(phase = AuthPhase.SIGNED_OUT) }
    }
    private val group = Group(1, "그룹", "설명", 3, 20, Membership.APPROVED)

    private fun scenario(block: suspend (MemberSelectionViewModel, Auth) -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                val auth = Auth()
                try { withTimeout(5000) { block(MemberSelectionViewModel(auth, scope), auth) } }
                finally { scope.cancel() }
            }
        }
    }

    @Test fun entryHasGroupContextButNeverFabricatesASuccessfulEmptyList() = scenario { model, _ ->
        model.enter(group)
        val initial = model.uiState.value
        model.enter(group); model.toggle(2); model.complete()
        assertEquals(initial, model.uiState.value)
        assertEquals(group, initial.group)
        assertEquals(MemberSelectionStatus.NOT_CONNECTED, initial.status)
        assertFalse(initial.canComplete)
        assertFalse(initial.canRefresh)
        assertFalse(initial.showDeveloping)
    }

    @Test fun leaveReentryAndGroupChangeStartWithCleanMemberContext() = scenario { model, _ ->
        model.enter(group)
        model.reset()
        assertNull(model.uiState.value.group)
        model.enter(group.copy(id = 2, name = "다른 그룹"))
        assertEquals(2L, model.uiState.value.group?.id)
        assertTrue(model.uiState.value.selectedUserIds.isEmpty())
        model.enter(group)
        assertEquals(1L, model.uiState.value.group?.id)
        assertFalse(model.uiState.value.showDeveloping)
    }

    @Test fun logoutAndAccountChangeClearAndRejectOldActions() = scenario { model, auth ->
        model.enter(group)
        auth.logout()
        while (model.uiState.value.group != null) yield()
        model.enter(group); model.toggle(2); model.complete()
        assertNull(model.uiState.value.group)
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(8, "다른 사용자"), true)
        model.enter(group)
        auth.state.value = auth.state.value.copy(user = AuthUser(9, "새 사용자"))
        model.complete()
        while (model.uiState.value.group != null) yield()
        assertFalse(model.uiState.value.showDeveloping)
    }

    @Test fun disconnectedSessionDoesNotTurnUnconnectedScreenIntoContent() = scenario { model, auth ->
        model.enter(group)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.RECONNECTING)
        yield()
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        yield()
        assertEquals(MemberSelectionStatus.NOT_CONNECTED, model.uiState.value.status)
        assertFalse(model.uiState.value.connected)
        assertFalse(model.uiState.value.snapshotConfirmed)
    }
}
