package com.example.ridemate_api_test

import android.app.Application
import com.example.ridemate_api_test.core.di.AppContainer

class RideMateApplication : Application() {
    val container by lazy { AppContainer() }
}
