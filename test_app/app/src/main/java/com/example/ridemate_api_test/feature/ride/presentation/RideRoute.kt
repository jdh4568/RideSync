package com.example.ridemate_api_test.feature.ride.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.feature.ride.domain.model.RideDestination
import com.example.ridemate_api_test.feature.ride.domain.model.RideRole
import com.example.ridemate_api_test.feature.ride.presentation.leader.LeaderRideScreen
import com.example.ridemate_api_test.feature.ride.presentation.follower.FollowerRideScreen

@Composable
fun RideRoute(destination: RideDestination, onBackHome: () -> Unit) {
    // Back only changes presentation; the session ID and role remain with the authenticated owner.
    BackHandler(onBack = onBackHome)
    HomeTheme {
        when (destination.role) {
            RideRole.LEADER -> LeaderRideScreen()
            RideRole.MEMBER -> FollowerRideScreen()
        }
    }
}
