package com.example.ridemate_api_test.feature.group.domain.model

/** 그룹 가입 상태. membershipStatus 값에 따라 버튼 상태가 결정된다. */
enum class MembershipStatus {
    NONE,       // 미가입 (null) — 가입 신청 가능
    REJECTED,   // 거절됨 — 재신청 가능
    PENDING,    // 신청중 (비활성)
    APPROVED,   // 가입됨 (비활성)
}

data class GroupUiModel(
    val groupId: Long,
    val groupName: String,
    val description: String,
    val memberCount: Int,
    val maxMembers: Int,
    val membershipStatus: MembershipStatus,
    val myRole: String? = null,
)

data class JoinRequestUiModel(
    val groupMemberId: Long,
    val groupId: Long,
    val groupName: String,
    val userId: Long,
    val nickname: String,
)
