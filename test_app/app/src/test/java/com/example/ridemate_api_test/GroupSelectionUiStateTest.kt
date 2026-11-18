package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.group.domain.model.Group
import com.example.ridemate_api_test.feature.group.domain.model.GroupProblem
import com.example.ridemate_api_test.feature.group.domain.model.Membership
import com.example.ridemate_api_test.feature.group.presentation.selection.*
import org.junit.Assert.*
import org.junit.Test

class GroupSelectionUiStateTest {
    private fun content() = GroupSelectionUiState(status = GroupSelectionStatus.CONTENT,
        groups = listOf(Group(1, "그룹1", "", 1, 2, Membership.APPROVED),
            Group(2, "그룹2", "", 2, 2, Membership.APPROVED)))

    @Test fun selectionIsExplicitSingleAndAllowsAFullGroup() {
        val initial = content()
        assertNull(initial.selectedGroupId)
        assertFalse(initial.canComplete)
        assertFalse(initial.openMembers().memberSelectionOpen)
        val selected = initial.select(1).select(2)
        assertEquals(2L, selected.selectedGroupId)
        assertTrue(selected.canComplete)
        assertEquals(selected, selected.select(999))
    }

    @Test fun onlyAGroupInTheCurrentSuccessfulListCanComplete() {
        val selected = content().select(1)
        for (status in listOf(GroupSelectionStatus.LOADING, GroupSelectionStatus.ERROR)) {
            val blocked = selected.copy(status = status)
            assertFalse(blocked.canComplete)
            assertEquals(blocked, blocked.select(2).openMembers())
        }
        assertFalse(selected.copy(groups = emptyList()).openMembers().memberSelectionOpen)
        assertFalse(selected.copy(problem = GroupProblem.NETWORK).openMembers().memberSelectionOpen)
        assertFalse(content().copy(groups = emptyList()).canComplete)
    }

    @Test fun memberHandoffBlocksRepeatedActionsAndReturnPreservesSelection() {
        val opened = content().select(1).openMembers()
        assertTrue(opened.memberSelectionOpen)
        assertFalse(opened.canComplete)
        assertEquals(opened, opened.openMembers().select(2))
        val dismissed = opened.copy(memberSelectionOpen = false)
        assertEquals(1L, dismissed.selectedGroupId)
        assertTrue(dismissed.canComplete)
    }
}
