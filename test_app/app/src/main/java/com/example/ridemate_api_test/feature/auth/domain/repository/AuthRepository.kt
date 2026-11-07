package com.example.ridemate_api_test.feature.auth.domain.repository

import com.example.ridemate_api_test.feature.auth.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val state: StateFlow<AuthState>
    fun login(loginId: String, password: String)
    fun retryConnection()
    fun logout()
}
