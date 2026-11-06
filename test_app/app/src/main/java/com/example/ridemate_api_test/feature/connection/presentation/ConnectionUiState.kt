package com.example.ridemate_api_test.feature.connection.presentation

data class ConnectionUiState(
    val baseUrl: String = "http://127.0.0.1:8080",
    val isChecking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)
