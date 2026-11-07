package com.example.ridemate_api_test.feature.auth.domain.model

data class AuthUser(val id: Long, val nickname: String)

// Deliberately not a data class: generated toString/copy must not expose a token.
class AuthCredentials(val accessToken: String, val user: AuthUser, val expiresAtMillis: Long) {
    override fun toString() = "AuthCredentials([REDACTED])"
}

enum class AuthPhase { INITIALIZING, SIGNED_OUT, AUTHENTICATING, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED }
enum class AuthProblem { INVALID_CREDENTIALS, INVALID_REQUEST, EXPIRED, FORBIDDEN, NETWORK, SERVER, INVALID_RESPONSE, STORAGE }
class AuthFailure(val problem: AuthProblem) : Exception(problem.name)

data class AuthState(
    val phase: AuthPhase = AuthPhase.INITIALIZING,
    val user: AuthUser? = null,
    val enteredSession: Boolean = false,
    val problem: AuthProblem? = null,
) {
    val isBusy: Boolean get() = phase in setOf(AuthPhase.INITIALIZING, AuthPhase.AUTHENTICATING, AuthPhase.CONNECTING, AuthPhase.RECONNECTING)
}
