package com.example.ridemate_api_test.feature.group.presentation.members

import com.example.ridemate_api_test.feature.group.domain.model.Group
import com.example.ridemate_api_test.feature.group.domain.model.GroupProblem

enum class MemberSelectionStatus { LOADING, WAITING_CONNECTION, CONTENT, ERROR }

/** OWNER/self are removed using server roles and the session user ID. */
data class MemberChoiceUi(val userId: Long, val nickname: String, val online: Boolean)

data class MemberSelectionUiState(
    val group: Group? = null,
    val status: MemberSelectionStatus = MemberSelectionStatus.LOADING,
    val members: List<MemberChoiceUi> = emptyList(),
    val selectedUserIds: Set<Long> = emptySet(),
    val connected: Boolean = false,
    // CONNECTED alone cannot validate a snapshot after a disconnect or failed refresh.
    val snapshotConfirmed: Boolean = false,
    val hasSnapshot: Boolean = false,
    val problem: GroupProblem? = null,
    val showDeveloping: Boolean = false,
    val selectionLimitReached: Boolean = false,
    val selectionChanged: Boolean = false,
) {
    val canSelect: Boolean get() = group?.id?.let { it > 0 } == true &&
        status == MemberSelectionStatus.CONTENT && problem == null && connected && snapshotConfirmed && !showDeveloping
    val canRefresh: Boolean get() = status in setOf(MemberSelectionStatus.CONTENT, MemberSelectionStatus.ERROR) &&
        group?.id?.let { it > 0 } == true && connected && !showDeveloping &&
        problem !in setOf(GroupProblem.FORBIDDEN, GroupProblem.NOT_FOUND, GroupProblem.UNAUTHORIZED)
    val canComplete: Boolean get() = canSelect && selectedUserIds.size in 1..49 &&
        selectedUserIds.all { id -> members.any { it.userId == id && id > 0 && it.online } }

    fun toggle(userId: Long): MemberSelectionUiState {
        if (!canSelect || members.none { it.userId == userId && userId > 0 && it.online }) return this
        if (userId in selectedUserIds) return copy(selectedUserIds = selectedUserIds - userId, selectionLimitReached = false)
        if (selectedUserIds.size >= 49) return copy(selectionLimitReached = true)
        return copy(selectedUserIds = selectedUserIds + userId, selectionLimitReached = false)
    }

    // group.id and selectedUserIds are the handoff for the future REST invitation request.
    fun complete(): MemberSelectionUiState = if (canComplete) copy(showDeveloping = true) else this
}
