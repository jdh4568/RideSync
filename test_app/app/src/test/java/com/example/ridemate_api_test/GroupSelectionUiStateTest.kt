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
        assertFalse(initial.complete().showDeveloping)
        val selected = initial.select(1).select(2)
        assertEquals(2L, selected.selectedGroupId)
        assertTrue(selected.canComplete)
        assertEquals(selected, selected.select(999))
    }

    @Test fun onlyAGroupInTheCurrentSuccessfulListCanComplete() {
        val selected = content().select(1)
        for (status in listOf(GroupSelectionStatus.NOT_CONNECTED, GroupSelectionStatus.LOADING, GroupSelectionStatus.ERROR)) {
            val blocked = selected.copy(status = status)
            assertFalse(blocked.canComplete)
            assertEquals(blocked, blocked.select(2).complete())
        }
        assertFalse(selected.copy(groups = emptyList()).complete().showDeveloping)
        assertFalse(selected.copy(problem = GroupProblem.NETWORK).complete().showDeveloping)
        assertFalse(content().copy(groups = emptyList()).canComplete)
    }

    @Test fun developingDialogBlocksRepeatedActionsAndDismissalPreservesSelection() {
        val opened = content().select(1).complete()
        assertTrue(opened.showDeveloping)
        assertFalse(opened.canComplete)
        assertEquals(opened, opened.complete().select(2))
        val dismissed = opened.copy(showDeveloping = false)
        assertEquals(1L, dismissed.selectedGroupId)
        assertTrue(dismissed.canComplete)
    }
}
