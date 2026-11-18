package com.example.ridemate_api_test.feature.group.presentation.selection

import com.example.ridemate_api_test.feature.group.domain.model.Group
import com.example.ridemate_api_test.feature.group.domain.model.GroupProblem

enum class GroupSelectionStatus { LOADING, CONTENT, ERROR }

data class GroupSelectionUiState(
    val status: GroupSelectionStatus = GroupSelectionStatus.LOADING,
    val groups: List<Group> = emptyList(),
    val selectedGroupId: Long? = null,
    val problem: GroupProblem? = null,
    val memberSelectionOpen: Boolean = false,
) {
    val canSelect: Boolean get() = status == GroupSelectionStatus.CONTENT && problem == null && !memberSelectionOpen
    val canComplete: Boolean get() = canSelect && groups.any { it.id == selectedGroupId }

    fun select(groupId: Long): GroupSelectionUiState =
        if (canSelect && groups.any { it.id == groupId }) copy(selectedGroupId = groupId) else this

    fun openMembers(): GroupSelectionUiState = if (canComplete) copy(memberSelectionOpen = true) else this
}
