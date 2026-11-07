package com.example.ridemate_api_test.feature.auth.presentation.login

enum class LoginIdError { REQUIRED, TOO_LONG }
enum class LoginNotice { LOGIN_UNAVAILABLE, SIGNUP_UNAVAILABLE }

data class LoginUiState(
    val loginId: String = "",
    val password: String = "",
    val loginIdError: LoginIdError? = null,
    val passwordRequired: Boolean = false,
    // Reserved for the real request lifecycle; never set by a simulated login.
    val isSubmitting: Boolean = false,
    val notice: LoginNotice? = null,
)
