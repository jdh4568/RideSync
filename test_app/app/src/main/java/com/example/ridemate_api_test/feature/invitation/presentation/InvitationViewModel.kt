package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Authenticated-area presentation owner. Transport/repository integration belongs to stage 2. */
class InvitationViewModel(
    private val auth: AuthRepository,
    testScope: CoroutineScope? = null,
    initialState: InvitationUiState = InvitationUiState(),
) : ViewModel() {
    // The application factory always uses empty state; fixtures are confined to tests/Preview.
    private val mutableState = MutableStateFlow(
        if (auth.state.value.enteredSession) initialState else InvitationUiState(),
    )
    val uiState = mutableState.asStateFlow()
    private var ownerId = auth.state.value.user?.id

    init {
        (testScope ?: viewModelScope).launch {
            auth.state.collect { current ->
                if (!current.enteredSession || current.user?.id != ownerId) {
                    mutableState.value = InvitationUiState()
                }
                ownerId = current.user?.id
            }
        }
    }

    private fun update(change: (InvitationUiState) -> InvitationUiState) {
        val current = auth.state.value
        if (!current.enteredSession || current.user?.id == null || current.user.id != ownerId) return
        mutableState.update(change)
    }

    fun hidePanel() = update { it.hidePanel() }
    fun reopen() = update { it.reopen() }
    fun openRide() = update { it.openRide() }
    fun backHome() = update { it.backHome() }
}
