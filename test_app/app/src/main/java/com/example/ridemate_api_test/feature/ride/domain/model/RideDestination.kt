package com.example.ridemate_api_test.feature.ride.domain.model

enum class RideRole { LEADER, MEMBER }

/** Supplied only after a confirmed server start and an unambiguous role. */
data class RideDestination(val rideSessionId: Long, val groupId: Long, val role: RideRole) {
    init { require(rideSessionId > 0 && groupId > 0) }
}
