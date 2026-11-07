package com.example.ridemate_api_test.core.di

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginViewModel
import com.example.ridemate_api_test.feature.connection.data.repository.ConnectionRepositoryImpl
import com.example.ridemate_api_test.feature.connection.domain.repository.ConnectionRepository

class AppContainer {
    val loginViewModelFactory = viewModelFactory { initializer { LoginViewModel() } }
    val connectionRepository: ConnectionRepository = ConnectionRepositoryImpl()
}
