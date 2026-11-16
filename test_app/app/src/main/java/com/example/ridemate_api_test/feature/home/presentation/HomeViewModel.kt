package com.example.ridemate_api_test.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.model.AuthPhase
import com.example.ridemate_api_test.feature.auth.domain.model.AuthState
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Observes the app-owned session; never creates a connection or repeats login. */
class HomeViewModel(
    private val repository: AuthRepository,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState().withSession(repository.state.value))
    val uiState = mutableState.asStateFlow()

    init {
        (testScope ?: viewModelScope).launch {
            var previousUserId = repository.state.value.user?.id
            repository.state.collect { auth ->
                val changedUser = previousUserId != auth.user?.id
                previousUserId = auth.user?.id
                mutableState.update { current ->
                    val base = if (!auth.enteredSession || changedUser) HomeUiState() else current
                    base.withSession(auth)
                }
            }
        }
    }

    fun showNotice(notice: HomeNotice) {
        if (!repository.state.value.enteredSession) return
        mutableState.update { if (it.notice == null) it.copy(notice = notice) else it }
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun retryConnection() {
        val auth = repository.state.value
        if (auth.enteredSession && auth.phase == AuthPhase.DISCONNECTED) repository.retryConnection()
    }

    fun logout() {
        dismissNotice()
        if (repository.state.value.enteredSession) repository.logout()
    }
}

private fun HomeUiState.withSession(auth: AuthState): HomeUiState {
    val status = if (!auth.enteredSession) null else when (auth.phase) {
        AuthPhase.CONNECTED -> HomeConnectionStatus.CONNECTED
        AuthPhase.CONNECTING, AuthPhase.RECONNECTING -> HomeConnectionStatus.RECONNECTING
        AuthPhase.DISCONNECTED -> HomeConnectionStatus.DISCONNECTED
        else -> null
    }
    return copy(connectionStatus = status, connectionProblem = auth.problem.takeIf { status != null })
}
