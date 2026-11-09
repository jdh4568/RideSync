package com.example.ridemate_api_test.feature.auth.presentation.signup

import com.example.ridemate_api_test.feature.auth.domain.repository.SignupProblem

enum class SignupField { LOGIN_ID, PASSWORD, PASSWORD_CONFIRM, NICKNAME }
enum class SignupError { REQUIRED, TOO_LONG, PASSWORD_TOO_LONG, PASSWORD_MISMATCH }
enum class Availability { UNCHECKED, CHECKING, AVAILABLE, TAKEN, ERROR }
data class AvailabilityState(val status: Availability = Availability.UNCHECKED, val problem: SignupProblem? = null)

data class SignupUiState(
    val loginId: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val nickname: String = "",
    val errors: Map<SignupField, SignupError> = emptyMap(),
    val checks: Map<SignupField, AvailabilityState> = emptyMap(),
    val isSubmitting: Boolean = false,
    val problem: SignupProblem? = null,
    val outcomeUncertain: Boolean = false,
    val completedLoginId: String? = null,
) {
    val canSubmit: Boolean get() = !isSubmitting && completedLoginId == null &&
        listOf(SignupField.LOGIN_ID, SignupField.NICKNAME).all { checks[it]?.status == Availability.AVAILABLE } &&
        SignupField.entries.all { validate(this, it) == null }
}

