package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.presentation.members.*
import org.junit.Assert.*
import org.junit.Test

class MemberSelectionUiStateTest {
    private fun content() = MemberSelectionUiState(
        group = Group(1, "그룹", "", 4, 50, Membership.APPROVED),
        status = MemberSelectionStatus.CONTENT, connected = true, snapshotConfirmed = true,
        members = listOf(MemberChoiceUi(2, "같은 이름", true), MemberChoiceUi(3, "같은 이름", true),
            MemberChoiceUi(4, "오프라인", false)))

    @Test fun explicitMultipleSelectionUsesIdsAndRejectsOfflineAndUnknownMembers() {
        val initial = content()
        assertFalse(initial.canComplete)
        assertFalse(initial.complete().showDeveloping)
        val selected = initial.toggle(2).toggle(3).toggle(4).toggle(999)
        assertEquals(setOf(2L, 3L), selected.selectedUserIds)
        assertTrue(selected.canComplete)
        assertEquals(setOf(3L), selected.toggle(2).selectedUserIds)
        assertFalse(selected.toggle(2).toggle(3).canComplete)
    }

    @Test fun selectionLimitDoesNotPreventRemovingASelectedMember() {
        var state = content().copy(members = (1L..50L).map { MemberChoiceUi(it, "멤버$it", true) })
        (1L..49L).forEach { state = state.toggle(it) }
        assertTrue(state.canComplete)
        state = state.toggle(50)
        assertEquals(49, state.selectedUserIds.size)
        assertTrue(state.selectionLimitReached)
        state = state.toggle(1).toggle(50)
        assertFalse(state.selectionLimitReached)
        assertEquals(49, state.selectedUserIds.size)
        assertTrue(50L in state.selectedUserIds)
    }

    @Test fun unconnectedLoadingErrorsAndUncertainConnectionCannotSelectOrComplete() {
        val selected = content().toggle(2)
        val blocked = MemberSelectionStatus.entries.filter { it != MemberSelectionStatus.CONTENT }
            .map { selected.copy(status = it) } + listOf(selected.copy(connected = false), selected.copy(snapshotConfirmed = false),
                selected.copy(problem = GroupProblem.NETWORK), selected.copy(group = null))
        blocked.forEach {
            assertFalse(it.canComplete)
            assertEquals(it, it.toggle(3).complete())
        }
        val uncertain = selected.copy(connected = false, snapshotConfirmed = false)
        assertTrue(uncertain.members.first().online) // Do not relabel everyone offline.
        assertFalse(uncertain.canRefresh)
        // A working app connection permits retry even when the last snapshot failed.
        val failedRefresh = selected.copy(status = MemberSelectionStatus.ERROR, snapshotConfirmed = false,
            problem = GroupProblem.NETWORK)
        assertTrue(failedRefresh.canRefresh)
        assertFalse(failedRefresh.canComplete)
    }

    @Test fun completionRevalidatesSelectionAndDialogKeepsHandoffWithoutSendingAnything() {
        val selected = content().toggle(2).toggle(3)
        assertFalse(selected.copy(members = selected.members.filter { it.userId != 2L }).canComplete)
        assertFalse(selected.copy(members = selected.members.map { it.copy(online = false) }).canComplete)
        val opened = selected.complete()
        assertTrue(opened.showDeveloping)
        assertEquals(opened, opened.complete().toggle(2))
        assertFalse(opened.canRefresh)
        val dismissed = opened.copy(showDeveloping = false)
        assertEquals(1L, dismissed.group?.id)
        assertEquals(setOf(2L, 3L), dismissed.selectedUserIds)
        assertTrue(dismissed.canComplete)
    }

    @Test fun emptyAndAllOfflineListsAndWaitingConnectionCannotComplete() {
        assertFalse(content().copy(members = emptyList()).canComplete)
        assertFalse(content().copy(members = listOf(MemberChoiceUi(4, "멤버", false))).toggle(4).canComplete)
        assertFalse(MemberSelectionUiState(group = content().group).canRefresh)
        assertFalse(content().copy(status = MemberSelectionStatus.WAITING_CONNECTION).canComplete)
    }
}
