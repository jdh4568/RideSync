package com.example.ridemate_api_test.feature.group.presentation

enum class GroupListStatus { PREPARING, LOADING, CONTENT, ERROR }
enum class GroupMembership { NONE, REJECTED, PENDING, APPROVED, UNKNOWN }
enum class GroupJoinAction { APPLY, PENDING, JOINED, FULL, UNKNOWN }

data class GroupCardUi(
    val id: Long,
    val name: String,
    val description: String,
    val memberCount: Int,
    val maxMembers: Int,
    val membership: GroupMembership,
) {
    val joinAction: GroupJoinAction get() = when (membership) {
        GroupMembership.APPROVED -> GroupJoinAction.JOINED
        GroupMembership.PENDING -> GroupJoinAction.PENDING
        GroupMembership.UNKNOWN -> GroupJoinAction.UNKNOWN
        GroupMembership.NONE, GroupMembership.REJECTED ->
            if (memberCount >= maxMembers) GroupJoinAction.FULL else GroupJoinAction.APPLY
    }
}

data class GroupCreationForm(
    val name: String = "",
    val description: String = "",
    val maxMembers: String = "",
)

data class GroupManagementUiState(
    // Runtime starts here: sample groups exist only inside Preview functions.
    val listStatus: GroupListStatus = GroupListStatus.PREPARING,
    val groups: List<GroupCardUi> = emptyList(),
    val isRefreshing: Boolean = false,
    val joiningGroupId: Long? = null,
    val showCreation: Boolean = false,
    val form: GroupCreationForm = GroupCreationForm(),
    val isCreating: Boolean = false,
)
