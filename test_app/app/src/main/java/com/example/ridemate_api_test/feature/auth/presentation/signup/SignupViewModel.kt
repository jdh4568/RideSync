package com.example.ridemate_api_test.feature.auth.presentation.signup

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SignupViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(SignupUiState())
    val uiState = mutableState.asStateFlow()

    fun update(field: SignupField, value: String) {
        mutableState.update { state ->
            val next = when (field) {
                SignupField.LOGIN_ID -> state.copy(loginId = value)
                SignupField.PASSWORD -> state.copy(password = value)
                SignupField.PASSWORD_CONFIRM -> state.copy(passwordConfirm = value)
                SignupField.NICKNAME -> state.copy(nickname = value)
            }
            next.copy(errors = state.errors - field -
                (if (field == SignupField.PASSWORD) setOf(SignupField.PASSWORD_CONFIRM) else emptySet()),
                showUnavailableNotice = false)
        }
    }

    fun checkAvailability(field: SignupField) {
        if (field != SignupField.LOGIN_ID && field != SignupField.NICKNAME) return
        mutableState.update { state ->
            val error = validate(state, field)
            state.copy(errors = if (error == null) state.errors - field else state.errors + (field to error),
                showUnavailableNotice = error == null)
        }
    }

    fun submit() {
        mutableState.update { state ->
            val errors = SignupField.entries.mapNotNull { field ->
                validate(state, field)?.let { field to it }
            }.toMap()
            // Stage 1 has no transport, availability success, or signup success.
            state.copy(errors = errors, showUnavailableNotice = errors.isEmpty())
        }
    }

    fun dismissNotice() { mutableState.update { it.copy(showUnavailableNotice = false) } }
    fun reset() { mutableState.value = SignupUiState() }
}

internal fun validate(state: SignupUiState, field: SignupField): SignupError? {
    val value = when (field) {
        SignupField.LOGIN_ID -> state.loginId
        SignupField.PASSWORD -> state.password
        SignupField.PASSWORD_CONFIRM -> state.passwordConfirm
        SignupField.NICKNAME -> state.nickname
    }
    return when {
        value.isBlank() -> SignupError.REQUIRED
        (field == SignupField.LOGIN_ID || field == SignupField.NICKNAME) && value.length > 50 -> SignupError.TOO_LONG
        field == SignupField.PASSWORD && value.toByteArray(Charsets.UTF_8).size > 72 -> SignupError.PASSWORD_TOO_LONG
        field == SignupField.PASSWORD_CONFIRM && value != state.password -> SignupError.PASSWORD_MISMATCH
        else -> null
    }
}

