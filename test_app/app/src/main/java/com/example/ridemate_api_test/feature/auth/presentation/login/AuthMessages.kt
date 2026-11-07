package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.annotation.StringRes
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.feature.auth.domain.model.AuthPhase
import com.example.ridemate_api_test.feature.auth.domain.model.AuthProblem

@StringRes
fun AuthProblem.messageResource(): Int = when (this) {
    AuthProblem.INVALID_CREDENTIALS -> R.string.auth_invalid_credentials
    AuthProblem.INVALID_REQUEST -> R.string.auth_invalid_request
    AuthProblem.EXPIRED -> R.string.auth_expired
    AuthProblem.FORBIDDEN -> R.string.auth_forbidden
    AuthProblem.NETWORK -> R.string.auth_network_error
    AuthProblem.SERVER -> R.string.auth_server_error
    AuthProblem.INVALID_RESPONSE -> R.string.auth_invalid_response
    AuthProblem.STORAGE -> R.string.auth_storage_error
}

@StringRes
fun AuthPhase.messageResource(): Int = when (this) {
    AuthPhase.INITIALIZING -> R.string.auth_initializing
    AuthPhase.SIGNED_OUT -> R.string.login_submit
    AuthPhase.AUTHENTICATING -> R.string.login_submitting
    AuthPhase.CONNECTING -> R.string.auth_connecting
    AuthPhase.CONNECTED -> R.string.auth_connected
    AuthPhase.RECONNECTING -> R.string.auth_reconnecting
    AuthPhase.DISCONNECTED -> R.string.auth_disconnected
}
