package com.example.ridemate_api_test.feature.connection.presentation

import com.example.ridemate_api_test.BuildConfig
data class ConnectionUiState(
    val baseUrl: String = BuildConfig.SERVER_BASE_URL,
    val isChecking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)
