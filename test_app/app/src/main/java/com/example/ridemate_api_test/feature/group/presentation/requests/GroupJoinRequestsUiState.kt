package com.example.ridemate_api_test.feature.group.presentation.requests

enum class GroupJoinRequestsStatus { PREPARING, CONTENT, EMPTY }

/** Presentation data; no sample requests are supplied to the runtime route. */
data class GroupJoinRequestItem(val id: Long, val nickname: String, val groupName: String)

data class GroupJoinRequestsUiState(
    val status: GroupJoinRequestsStatus = GroupJoinRequestsStatus.PREPARING,
    val requests: List<GroupJoinRequestItem> = emptyList(),
)
