package com.example.ridemate_api_test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ridemate_api_test.app.navigation.RideMateApp
import com.example.ridemate_api_test.core.designsystem.theme.RideMate_api_testTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as RideMateApplication).container.session.setForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) (application as RideMateApplication).container.session.setForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RideMateApplication).container
        setContent { RideMate_api_testTheme { RideMateApp(container) } }
    }
}
