package com.example.ridemate_api_test.feature.group.presentation.requests

import com.example.ridemate_api_test.feature.group.domain.model.*

enum class GroupJoinRequestsStatus { LOADING, CONTENT, EMPTY, NON_LEADER, ERROR }
enum class GroupRequestsNotice { APPROVED, REJECTED, UNCERTAIN, CHANGED }

data class GroupJoinRequestsUiState(
    val status: GroupJoinRequestsStatus = GroupJoinRequestsStatus.LOADING,
    val requests: List<GroupJoinRequest> = emptyList(),
    val visible: Boolean = false,
    val refreshing: Boolean = false,
    val processingId: Long? = null,
    val listProblem: GroupProblem? = null,
    val actionProblem: GroupProblem? = null,
    val notice: GroupRequestsNotice? = null,
    val needsVerification: Boolean = false,
    val fullGroupIds: Set<Long> = emptySet(),
) {
    val canRespond get() = visible && status == GroupJoinRequestsStatus.CONTENT && !refreshing &&
        processingId == null && !needsVerification && listProblem == null
}
