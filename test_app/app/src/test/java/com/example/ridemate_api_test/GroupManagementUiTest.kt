package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.group.presentation.*
import org.junit.Assert.*
import org.junit.Test

class GroupManagementUiTest {
    @Test
    fun membershipTakesPriorityOverCapacityAndRejectedUsersCanReapply() {
        val group = GroupCardUi(1, "그룹", "", 10, 10, GroupMembership.NONE)
        assertEquals(GroupJoinAction.FULL, group.joinAction)
        assertEquals(GroupJoinAction.FULL, group.copy(memberCount = 11).joinAction)
        assertEquals(GroupJoinAction.PENDING, group.copy(membership = GroupMembership.PENDING).joinAction)
        assertEquals(GroupJoinAction.JOINED, group.copy(membership = GroupMembership.APPROVED).joinAction)
        assertEquals(GroupJoinAction.UNKNOWN, group.copy(membership = GroupMembership.UNKNOWN).joinAction)
        assertEquals(GroupJoinAction.APPLY, group.copy(memberCount = 9).joinAction)
        assertEquals(GroupJoinAction.APPLY,
            group.copy(memberCount = 9, membership = GroupMembership.REJECTED).joinAction)
        assertEquals(GroupJoinAction.FULL, group.copy(membership = GroupMembership.REJECTED).joinAction)
    }

    @Test
    fun cancelAndLeavingDiscardDraftWithoutCreatingSampleGroups() {
        val model = GroupManagementViewModel()
        assertEquals(GroupListStatus.PREPARING, model.uiState.value.listStatus)
        assertTrue(model.uiState.value.groups.isEmpty())
        model.openCreation()
        val draft = GroupCreationForm("내 그룹", "설명", "12")
        model.updateForm(draft)
        model.openCreation() // Duplicate taps must not erase an open draft.
        assertEquals(draft, model.uiState.value.form)
        model.dismissCreation()
        assertFalse(model.uiState.value.showCreation)
        assertEquals(GroupCreationForm(), model.uiState.value.form)
        model.openCreation()
        model.updateForm(draft)
        model.reset() // The same ViewModel can remain in the Activity store after leaving.
        assertEquals(GroupManagementUiState(), model.uiState.value)
        model.updateForm(draft)
        assertEquals(GroupCreationForm(), model.uiState.value.form)
    }
}
