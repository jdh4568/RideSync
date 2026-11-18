package com.example.ridemate_api_test.feature.group.domain.model

enum class GroupRole { OWNER, MEMBER }
data class GroupMember(val userId: Long, val nickname: String, val role: GroupRole, val online: Boolean)

/** Approved members, including OWNER/self, and presence as of the REST query. */
data class GroupMembers(
    val groupId: Long, val groupName: String, val description: String,
    val memberCount: Int, val members: List<GroupMember>,
)
