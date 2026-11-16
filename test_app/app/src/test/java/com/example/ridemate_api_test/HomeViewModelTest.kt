package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.home.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class HomeViewModelTest {
    private class Repository(initial: AuthState) : AuthRepository {
        override val state = MutableStateFlow(initial)
        var loginCalls = 0
        var retryCalls = 0
        var logoutCalls = 0
        override fun login(loginId: String, password: String) { loginCalls++ }
        override fun retryConnection() {
            retryCalls++
            state.value = state.value.copy(phase = AuthPhase.RECONNECTING, problem = null)
        }
        override fun logout() {
            logoutCalls++
            state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        }
    }

    private fun connected(userId: Long = 1) = AuthState(
        AuthPhase.CONNECTED, AuthUser(userId, "테스트"), enteredSession = true,
    )

    private fun scenario(initial: AuthState, block: (Repository, HomeViewModel) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = Repository(initial)
            block(repository, HomeViewModel(repository, scope))
        } finally { scope.cancel() }
    }

    @Test fun constructionAndStateCollectionNeverInitiateLoginOrConnection() = scenario(
        AuthState(AuthPhase.CONNECTING, AuthUser(1, "테스트")),
    ) { repository, model ->
        assertNull(model.uiState.value.connectionStatus)
        model.retryConnection()
        model.showNotice(HomeNotice.GROUPS)
        assertNull(model.uiState.value.notice)
        repository.state.value = connected()
        assertEquals(HomeConnectionStatus.CONNECTED, model.uiState.value.connectionStatus)
        assertEquals(0, repository.loginCalls)
        assertEquals(0, repository.retryCalls)
    }

    @Test fun reconnectUsesExistingRepositoryAndClearsThePreviousError() = scenario(connected()) { repository, model ->
        repository.state.value = connected().copy(phase = AuthPhase.RECONNECTING, problem = AuthProblem.NETWORK)
        assertEquals(HomeConnectionStatus.RECONNECTING, model.uiState.value.connectionStatus)
        assertEquals(AuthProblem.NETWORK, model.uiState.value.connectionProblem)
        model.retryConnection()
        assertEquals(0, repository.retryCalls)
        repository.state.value = repository.state.value.copy(phase = AuthPhase.DISCONNECTED)
        model.retryConnection()
        model.retryConnection()
        assertEquals(1, repository.retryCalls)
        assertEquals(HomeConnectionStatus.RECONNECTING, model.uiState.value.connectionStatus)
        assertNull(model.uiState.value.connectionProblem)
        repository.state.value = connected()
        assertEquals(HomeConnectionStatus.CONNECTED, model.uiState.value.connectionStatus)
        assertEquals(0, repository.loginCalls)
    }

    @Test fun offlineLogoutClearsNoticeAndCannotBeRepeatedFromStaleHome() = scenario(connected()) { repository, model ->
        repository.state.value = connected().copy(phase = AuthPhase.DISCONNECTED, problem = AuthProblem.NETWORK)
        model.showNotice(HomeNotice.GROUPS)
        model.showNotice(HomeNotice.START_RIDE)
        assertEquals(HomeNotice.GROUPS, model.uiState.value.notice)
        model.logout()
        model.logout()
        model.retryConnection()
        assertEquals(1, repository.logoutCalls)
        assertEquals(0, repository.retryCalls)
        assertNull(model.uiState.value.connectionStatus)
        assertNull(model.uiState.value.connectionProblem)
        assertNull(model.uiState.value.notice)
        repository.state.value = connected()
        assertNull(model.uiState.value.notice)
        assertEquals(HomeConnectionStatus.CONNECTED, model.uiState.value.connectionStatus)
    }

    @Test fun expiryRejectionAndAccountChangeNeverKeepThePreviousNotice() = scenario(connected()) { repository, model ->
        for (problem in listOf(AuthProblem.EXPIRED, AuthProblem.FORBIDDEN)) {
            model.showNotice(HomeNotice.NOTIFICATIONS)
            repository.state.value = AuthState(phase = AuthPhase.SIGNED_OUT, problem = problem)
            assertNull(model.uiState.value.notice)
            assertNull(model.uiState.value.connectionStatus)
            assertNull(model.uiState.value.connectionProblem)
            repository.state.value = connected()
            assertNull(model.uiState.value.notice)
        }
        model.showNotice(HomeNotice.GROUPS)
        repository.state.value = connected(2)
        assertNull(model.uiState.value.notice)
        assertEquals(HomeConnectionStatus.CONNECTED, model.uiState.value.connectionStatus)
    }
}
