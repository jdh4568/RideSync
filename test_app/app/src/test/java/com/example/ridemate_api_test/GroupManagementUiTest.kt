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
    fun formValidationMatchesServerBoundaries() {
        assertTrue(GroupCreationForm().errors().hasErrors)
        assertFalse(GroupCreationForm("가".repeat(100), "나".repeat(500), "2").errors().hasErrors)
        assertFalse(GroupCreationForm("그룹", "", "50").errors().hasErrors)
        assertEquals(GroupFieldError.NAME_REQUIRED, GroupCreationForm("  ", "", "2").errors().name)
        assertEquals(GroupFieldError.NAME_LONG, GroupCreationForm("가".repeat(101), "", "2").errors().name)
        assertEquals(GroupFieldError.DESCRIPTION_LONG, GroupCreationForm("그룹", "나".repeat(501), "2").errors().description)
        listOf("1", "51", "2.5", "-2", "x", "99999999999999999999").forEach {
            assertEquals(GroupFieldError.CAPACITY, GroupCreationForm("그룹", "", it).errors().capacity)
        }
    }
}
