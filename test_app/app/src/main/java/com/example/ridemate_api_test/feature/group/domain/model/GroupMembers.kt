package com.example.ridemate_api_test.feature.group.domain.model

/** 그룹 멤버 역할. */
enum class GroupMemberRole {
    OWNER,
    MEMBER,
}

/** `GET /api/v1/groups/{groupId}/members` 의 members 배열 항목. */
data class GroupMemberUiModel(
    val userId: Long,
    val nickname: String,
    val role: GroupMemberRole,
    val online: Boolean,
)

/**
 * 멤버 선택 화면(라이딩 준비 2)이 사용하는 그룹 정보 + 멤버 목록.
 *
 * - `description` 이 null/빈 문자열이면 화면에서 설명을 숨긴다.
 * - `memberCount` 는 본인을 포함한 `members` 개수이며, 선택 목록에서만 본인을 제외한다.
 */
data class GroupMembersUiModel(
    val groupId: Long,
    val groupName: String,
    val memberCount: Int,
    val description: String?,
    val members: List<GroupMemberUiModel>,
) {
    /** 선택 목록에 노출할 멤버: 본인 제외. */
    fun selectableMembers(selfUserId: Long): List<GroupMemberUiModel> =
        members.filter { it.userId != selfUserId }
}
