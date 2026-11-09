package com.example.ridemate_api_test.feature.auth.data.repository

import com.example.ridemate_api_test.feature.auth.data.remote.SignupApi
import com.example.ridemate_api_test.feature.auth.domain.repository.*

class SignupRepositoryImpl(private val api: SignupApi) : SignupRepository {
    override suspend fun check(identity: SignupIdentity, value: String) = api.check(identity, value)
    override suspend fun signup(request: SignupRequest) = api.signup(request)
}

