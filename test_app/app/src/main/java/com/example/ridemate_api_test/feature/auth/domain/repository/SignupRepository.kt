package com.example.ridemate_api_test.feature.auth.domain.repository

enum class SignupIdentity { LOGIN_ID, NICKNAME }
enum class SignupProblem {
    INVALID_REQUEST, PASSWORD_MISMATCH, INVALID_PASSWORD, DUPLICATE_LOGIN_ID,
    DUPLICATE_NICKNAME, DUPLICATE_USER, UNAUTHORIZED, FORBIDDEN, NETWORK, SERVER, INVALID_RESPONSE
}
class SignupFailure(val problem: SignupProblem) : Exception(problem.name)

// Never includes credentials in generated toString/log output.
class SignupRequest(val loginId: String, val password: String, val passwordConfirm: String, val nickname: String)
data class SignupResult(val userId: Long, val nickname: String)

interface SignupRepository {
    suspend fun check(identity: SignupIdentity, value: String): Boolean
    suspend fun signup(request: SignupRequest): SignupResult
}

