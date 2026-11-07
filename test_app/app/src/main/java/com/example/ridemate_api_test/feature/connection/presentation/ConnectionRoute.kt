package com.example.ridemate_api_test.feature.connection.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ridemate_api_test.feature.connection.domain.repository.ConnectionRepository

@Composable
fun ConnectionRoute(repository: ConnectionRepository) {
    val viewModel: ConnectionViewModel = viewModel(factory = viewModelFactory {
        initializer { ConnectionViewModel(repository) }
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionScreen(state, viewModel::updateBaseUrl, viewModel::checkConnection)
}
