package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { auth ->
                mutableState.update { it.copy(isSubmitting = auth.isBusy, phase = auth.phase,
                    problem = auth.problem, hasPendingSession = auth.user != null,
                    password = if (auth.user != null) "" else it.password) }
            }
        }
    }

    // Inputs survive activity recreation in memory, but passwords are not saved to disk/Bundle.
    fun updateLoginId(value: String) {
        if (!mutableState.value.isSubmitting && !mutableState.value.hasPendingSession) {
            mutableState.update { it.copy(loginId = value, loginIdError = null, notice = null) }
        }
    }

    fun updatePassword(value: String) {
        if (!mutableState.value.isSubmitting && !mutableState.value.hasPendingSession) {
            mutableState.update { it.copy(password = value, passwordRequired = false, notice = null) }
        }
    }

    fun submit() {
        if (mutableState.value.isSubmitting || mutableState.value.hasPendingSession) return
        mutableState.update { state ->
            val idError = when {
                state.loginId.isBlank() -> LoginIdError.REQUIRED
                state.loginId.length > 50 -> LoginIdError.TOO_LONG
                else -> null
            }
            val passwordRequired = state.password.isBlank()
            state.copy(
                loginIdError = idError,
                passwordRequired = passwordRequired,
                notice = null,
            )
        }
        val current = mutableState.value
        if (current.loginIdError == null && !current.passwordRequired) {
            repository.login(current.loginId, current.password)
        }
    }

    fun retryConnection() = repository.retryConnection()
    fun cancelLogin() { repository.logout(); mutableState.update { it.copy(password = "") } }

    fun requestSignup() {
        if (!mutableState.value.isSubmitting) {
            mutableState.update { it.copy(notice = LoginNotice.SIGNUP_UNAVAILABLE) }
        }
    }

    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
}
