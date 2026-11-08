package com.example.ridemate_api_test.feature.auth.presentation.signup

enum class SignupField { LOGIN_ID, PASSWORD, PASSWORD_CONFIRM, NICKNAME }
enum class SignupError { REQUIRED, TOO_LONG, PASSWORD_TOO_LONG, PASSWORD_MISMATCH }

data class SignupUiState(
    val loginId: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val nickname: String = "",
    val errors: Map<SignupField, SignupError> = emptyMap(),
    val showUnavailableNotice: Boolean = false,
)

