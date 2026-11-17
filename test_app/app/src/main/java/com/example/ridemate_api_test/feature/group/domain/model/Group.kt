package com.example.ridemate_api_test.feature.group.domain.model

enum class Membership { NONE, REJECTED, PENDING, APPROVED, UNKNOWN }

data class Group(
    val id: Long, val name: String, val description: String,
    val memberCount: Int, val maxMembers: Int, val membership: Membership,
)

data class CreateGroup(val name: String, val description: String, val maxMembers: Int)
data class CreatedGroup(val id: Long, val name: String)

enum class GroupProblem { NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, FULL,
    INVALID_REQUEST, SERVER, INVALID_RESPONSE }
class GroupFailure(val problem: GroupProblem) : Exception(problem.name)
