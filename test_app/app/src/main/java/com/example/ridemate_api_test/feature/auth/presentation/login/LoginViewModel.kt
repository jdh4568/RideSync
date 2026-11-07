package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LoginViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val uiState = mutableState.asStateFlow()

    // Inputs survive activity recreation in memory, but passwords are not saved to disk/Bundle.
    fun updateLoginId(value: String) {
        if (!mutableState.value.isSubmitting) {
            mutableState.update { it.copy(loginId = value, loginIdError = null, notice = null) }
        }
    }

    fun updatePassword(value: String) {
        if (!mutableState.value.isSubmitting) {
            mutableState.update { it.copy(password = value, passwordRequired = false, notice = null) }
        }
    }

    fun submit() {
        if (mutableState.value.isSubmitting) return
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
                notice = if (idError == null && !passwordRequired) LoginNotice.LOGIN_UNAVAILABLE else null,
            )
        }
    }

    fun requestSignup() {
        if (!mutableState.value.isSubmitting) {
            mutableState.update { it.copy(notice = LoginNotice.SIGNUP_UNAVAILABLE) }
        }
    }

    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
}
