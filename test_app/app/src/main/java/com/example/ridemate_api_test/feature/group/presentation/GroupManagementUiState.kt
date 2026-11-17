package com.example.ridemate_api_test.feature.group.presentation

import com.example.ridemate_api_test.feature.group.domain.model.GroupProblem

enum class GroupListStatus { LOADING, CONTENT, ERROR }
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

enum class GroupFieldError { NAME_REQUIRED, NAME_LONG, DESCRIPTION_LONG, CAPACITY }
data class GroupFormErrors(val name: GroupFieldError? = null, val description: GroupFieldError? = null,
    val capacity: GroupFieldError? = null) {
    val hasErrors get() = name != null || description != null || capacity != null
}
enum class GroupNotice { JOINED, CREATED, JOIN_UNCERTAIN }

internal fun GroupCreationForm.errors() = GroupFormErrors(
    name = when { name.isBlank() -> GroupFieldError.NAME_REQUIRED
        name.length > 100 -> GroupFieldError.NAME_LONG; else -> null },
    description = if (description.length > 500) GroupFieldError.DESCRIPTION_LONG else null,
    capacity = if (!maxMembers.matches(Regex("[0-9]+")) || maxMembers.toIntOrNull() !in 2..50)
        GroupFieldError.CAPACITY else null,
)

data class GroupManagementUiState(
    val listStatus: GroupListStatus = GroupListStatus.LOADING,
    val groups: List<GroupCardUi> = emptyList(),
    val isRefreshing: Boolean = false,
    val joiningGroupId: Long? = null,
    val showCreation: Boolean = false,
    val form: GroupCreationForm = GroupCreationForm(),
    val isCreating: Boolean = false,
    val listProblem: GroupProblem? = null,
    val actionProblem: GroupProblem? = null,
    val formProblem: GroupProblem? = null,
    val formErrors: GroupFormErrors = GroupFormErrors(),
    val creationUncertain: Boolean = false,
    val notice: GroupNotice? = null,
)
