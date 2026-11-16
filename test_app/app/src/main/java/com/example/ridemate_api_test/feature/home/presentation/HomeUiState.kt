package com.example.ridemate_api_test.feature.home.presentation

import com.example.ridemate_api_test.feature.auth.domain.model.AuthProblem

/** Display samples only; these values are never represented as an API response. */
data class HomeStatistics(
    val distance: String = "50.2",
    val averageSpeed: String = "28.5",
    val rideCount: Int = 23,
    val lastRideDate: String = "2026.09.20",
)

data class HomeRecentRide(val groupName: String, val date: String, val distance: String)

enum class HomeNotice { NOTIFICATIONS, START_RIDE, GROUPS }
enum class HomeConnectionStatus { CONNECTED, RECONNECTING, DISCONNECTED }

/** A null connection status means the authenticated destination is not available. */
data class HomeUiState(
    val statistics: HomeStatistics = HomeStatistics(),
    val recentRides: List<HomeRecentRide> = listOf(
        HomeRecentRide("A그룹", "2026.09.20", "24.3"),
        HomeRecentRide("B그룹", "2026.09.19", "40.1"),
        HomeRecentRide("C그룹", "2026.09.18", "25.5"),
    ),
    val notice: HomeNotice? = null,
    val connectionStatus: HomeConnectionStatus? = null,
    val connectionProblem: AuthProblem? = null,
)
