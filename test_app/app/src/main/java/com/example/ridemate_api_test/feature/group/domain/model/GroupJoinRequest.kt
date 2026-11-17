package com.example.ridemate_api_test.feature.group.domain.model

data class GroupJoinRequest(
    val id: Long, val groupId: Long, val userId: Long, val nickname: String, val groupName: String,
)

enum class GroupRequestDecision(val path: String, val status: Membership) {
    APPROVE("approve", Membership.APPROVED), REJECT("reject", Membership.REJECTED),
}

data class GroupRequestResult(val groupId: Long, val userId: Long, val status: Membership)
