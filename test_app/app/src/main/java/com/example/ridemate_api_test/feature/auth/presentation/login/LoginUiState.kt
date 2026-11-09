package com.example.ridemate_api_test.feature.auth.presentation.login

import com.example.ridemate_api_test.feature.auth.domain.model.AuthPhase
import com.example.ridemate_api_test.feature.auth.domain.model.AuthProblem
enum class LoginIdError { REQUIRED, TOO_LONG }
enum class LoginNotice { SIGNUP_COMPLETE }

data class LoginUiState(
    val loginId: String = "",
    val password: String = "",
    val loginIdError: LoginIdError? = null,
    val passwordRequired: Boolean = false,
    val isSubmitting: Boolean = false,
    val phase: AuthPhase = AuthPhase.SIGNED_OUT,
    val problem: AuthProblem? = null,
    val hasPendingSession: Boolean = false,
    val notice: LoginNotice? = null,
)
