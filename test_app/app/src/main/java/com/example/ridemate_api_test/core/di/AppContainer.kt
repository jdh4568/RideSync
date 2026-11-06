package com.example.ridemate_api_test.core.di

import com.example.ridemate_api_test.feature.connection.data.repository.ConnectionRepositoryImpl
import com.example.ridemate_api_test.feature.connection.domain.repository.ConnectionRepository

class AppContainer {
    val connectionRepository: ConnectionRepository = ConnectionRepositoryImpl()
}
