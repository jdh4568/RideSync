package com.example.ridemate_api_test.feature.auth.data.repository

import com.example.ridemate_api_test.app.session.LoginSession
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository

class AuthRepositoryImpl(private val session: LoginSession) : AuthRepository {
    override val state = session.state
    override fun login(loginId: String, password: String) = session.login(loginId, password)
    override fun retryConnection() = session.retryConnection()
    override fun logout() = session.logout()
}
