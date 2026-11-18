package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import com.example.ridemate_api_test.feature.group.presentation.members.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class MemberSelectionViewModelTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true))
        override fun login(loginId: String, password: String) = error("No login command")
        override fun retryConnection() = error("No screen-owned socket connection")
        override fun logout() { state.value = AuthState(phase = AuthPhase.SIGNED_OUT) }
    }
    private class Repository : GroupRepository {
        val calls = mutableListOf<Long>()
        var finished = 0
        var fetch: suspend (Long) -> GroupMembers = { snapshot(it) }
        override suspend fun members(groupId: Long): GroupMembers {
            calls += groupId
            try { return fetch(groupId) } finally { finished++ }
        }
        override suspend fun ownedGroups(): List<Group> = error("No extra owned-list query")
        override suspend fun groups(): List<Group> = error("Not used")
        override suspend fun join(groupId: Long) = error("No mutation")
        override suspend fun create(request: CreateGroup): CreatedGroup = error("No mutation")
        override suspend fun joinRequests(): List<GroupJoinRequest> = error("Not used")
        override suspend fun respondToJoinRequest(groupId: Long, userId: Long, decision: GroupRequestDecision): GroupRequestResult = error("No mutation")
    }
    private class Fixture(scope: CoroutineScope) {
        val auth = Auth()
        val repo = Repository()
        val model = MemberSelectionViewModel(repo, auth, scope)
        suspend fun start(id: Long = 1) { model.enter(group(id)); await { model.uiState.value.status == MemberSelectionStatus.CONTENT } }
        suspend fun phase(phase: AuthPhase) {
            auth.state.value = auth.state.value.copy(phase = phase)
            await { model.uiState.value.connected == (phase == AuthPhase.CONNECTED) }
            yield()
        }
    }
    private fun scenario(block: suspend Fixture.() -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                try { withTimeout(8000) { Fixture(scope).block() } } finally { scope.cancel() }
            }
        }
    }

    @Test fun initialQueryIsDeduplicatedAndServerRolesAndIdsDetermineChoices() = scenario {
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { gate.await(); snapshot(it) }
        model.enter(group()); model.enter(group()); model.refresh(); model.toggle(2); model.complete()
        await { repo.calls.size == 1 }
        assertEquals(MemberSelectionStatus.LOADING, model.uiState.value.status)
        assertFalse(model.uiState.value.hasSnapshot)
        assertFalse(model.uiState.value.canComplete)
        gate.complete(Unit)
        await { model.uiState.value.snapshotConfirmed }
        val state = model.uiState.value
        assertEquals(listOf(2L, 3L), state.members.map { it.userId })
        assertEquals("서버 그룹", state.group?.name)
        assertEquals(3, state.group?.memberCount) // Includes owner, not the two choices.
        assertNull(state.group?.description?.takeIf { it.isNotEmpty() })
        assertTrue(state.selectedUserIds.isEmpty())
        model.enter(group()); yield()
        assertEquals(1, repo.calls.size)
    }

    @Test fun onlyOnlineMembersCanCompleteAndPopupDoesNotSendOrRefetch() = scenario {
        start(); model.toggle(7); model.toggle(999); model.toggle(3); model.complete()
        assertTrue(model.uiState.value.selectedUserIds.isEmpty())
        model.toggle(2); model.complete(); model.complete(); model.toggle(2); model.refresh()
        assertTrue(model.uiState.value.showDeveloping)
        assertEquals(setOf(2L), model.uiState.value.selectedUserIds)
        assertEquals(1, repo.calls.size)
        model.dismissDeveloping()
        assertTrue(model.uiState.value.canComplete)
    }

    @Test fun invitationHandoffUsesCurrentConfirmedSelectionAndRejectsStaleSnapshots() = scenario {
        start()
        assertNull(model.invitationSelection())
        model.toggle(2)
        val captured = model.invitationSelection()
        assertEquals(1L to setOf(2L), captured)
        model.toggle(2)
        assertNull(model.invitationSelection())
        assertEquals(setOf(2L), captured?.second)
        model.toggle(2)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        await { !model.uiState.value.connected }
        assertNull(model.invitationSelection())
        model.reset()
        assertNull(model.invitationSelection())
    }

    @Test fun refreshPreservesOnlyStillOnlinePresentSelectionsAndReportsRemoval() = scenario {
        repo.fetch = { snapshot(it, choices = listOf(member(2), member(3), member(4))) }
        start(); model.toggle(2); model.toggle(3); model.toggle(4)
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { gate.await(); snapshot(it, choices = listOf(member(2, false), member(4), member(5))) }
        model.refresh(); model.refresh()
        await { repo.calls.size == 2 }
        assertFalse(model.uiState.value.canComplete)
        assertEquals(setOf(2L, 3L, 4L), model.uiState.value.selectedUserIds)
        assertEquals(3, model.uiState.value.members.size)
        gate.complete(Unit)
        await { model.uiState.value.snapshotConfirmed }
        assertEquals(setOf(4L), model.uiState.value.selectedUserIds)
        assertTrue(model.uiState.value.selectionChanged)
        repo.fetch = { snapshot(it, choices = emptyList()) }
        model.refresh()
        await { model.uiState.value.snapshotConfirmed }
        assertTrue(model.uiState.value.members.isEmpty())
        assertTrue(model.uiState.value.hasSnapshot)
        assertEquals(1, model.uiState.value.group?.memberCount)
        assertFalse(model.uiState.value.canComplete)
    }

    @Test fun firstFailureIsNotEmptySuccessAndRetryIsExplicitAndDeduplicated() = scenario {
        for (problem in listOf(GroupProblem.NETWORK, GroupProblem.SERVER, GroupProblem.INVALID_RESPONSE)) {
            repo.fetch = { throw GroupFailure(problem) }
            model.enter(group())
            await { model.uiState.value.status == MemberSelectionStatus.ERROR }
            assertEquals(problem, model.uiState.value.problem)
            assertFalse(model.uiState.value.hasSnapshot)
            assertFalse(model.uiState.value.canComplete)
            val before = repo.calls.size
            val gate = CompletableDeferred<Unit>()
            repo.fetch = { gate.await(); snapshot(it) }
            model.refresh(); model.refresh()
            await { repo.calls.size == before + 1 }
            gate.complete(Unit)
            await { model.uiState.value.snapshotConfirmed }
            model.reset()
        }
    }

    @Test fun failedRefreshKeepsLastSnapshotButBlocksCompletionUntilSuccessfulRetry() = scenario {
        start(); model.toggle(2)
        repo.fetch = { throw GroupFailure(GroupProblem.NETWORK) }
        model.refresh()
        await { model.uiState.value.status == MemberSelectionStatus.ERROR }
        assertTrue(model.uiState.value.hasSnapshot)
        assertTrue(model.uiState.value.members.first().online)
        assertEquals(setOf(2L), model.uiState.value.selectedUserIds)
        assertFalse(model.uiState.value.canComplete)
        assertTrue(model.uiState.value.canRefresh)
        repo.fetch = { snapshot(it) }; model.refresh()
        await { model.uiState.value.snapshotConfirmed }
        assertTrue(model.uiState.value.canComplete)
    }

    @Test fun ownerAuthorityIsRequiredAndWrongGroupResponseIsRejected() = scenario {
        for ((response, problem) in listOf(
            snapshot().let { it.copy(members = it.members.map { m -> m.copy(role = GroupRole.MEMBER) }) } to GroupProblem.FORBIDDEN,
            snapshot().let { it.copy(members = it.members.filter { m -> m.userId != 7L }) } to GroupProblem.FORBIDDEN,
            snapshot(9) to GroupProblem.INVALID_RESPONSE)) {
            repo.fetch = { response }; model.enter(group())
            await { model.uiState.value.status == MemberSelectionStatus.ERROR }
            assertEquals(problem, model.uiState.value.problem)
            assertFalse(model.uiState.value.canComplete)
            model.reset()
        }
    }

    @Test fun terminalPermissionAndMissingGroupErrorsClearChoicesAndDoNotAutoRetry() = scenario {
        for (problem in listOf(GroupProblem.FORBIDDEN, GroupProblem.NOT_FOUND)) {
            repo.fetch = { snapshot(it) }; start(); model.toggle(2)
            repo.fetch = { throw GroupFailure(problem) }; model.refresh()
            await { model.uiState.value.status == MemberSelectionStatus.ERROR }
            val before = repo.calls.size
            assertFalse(model.uiState.value.hasSnapshot)
            assertTrue(model.uiState.value.selectedUserIds.isEmpty())
            model.refresh(); phase(AuthPhase.DISCONNECTED); phase(AuthPhase.CONNECTED)
            assertEquals(problem, model.uiState.value.problem)
            assertEquals(MemberSelectionStatus.ERROR, model.uiState.value.status)
            assertEquals(before, repo.calls.size)
            model.reset()
        }
    }

    @Test fun disconnectKeepsSnapshotAndRecoveryQueriesOnceWithoutRestoringTrustEarly() = scenario {
        start(); model.toggle(2)
        phase(AuthPhase.RECONNECTING)
        assertTrue(model.uiState.value.members.first().online)
        assertEquals(setOf(2L), model.uiState.value.selectedUserIds)
        assertFalse(model.uiState.value.snapshotConfirmed)
        model.complete(); assertFalse(model.uiState.value.showDeveloping)
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { gate.await(); snapshot(it, choices = listOf(member(2, false), member(3))) }
        phase(AuthPhase.CONNECTED)
        await { repo.calls.size == 2 }
        assertFalse(model.uiState.value.canComplete)
        auth.state.value = auth.state.value.copy(user = AuthUser(7, "닉네임 변경")); yield()
        model.enter(group()); model.refresh()
        assertEquals(2, repo.calls.size)
        gate.complete(Unit)
        await { model.uiState.value.snapshotConfirmed }
        assertTrue(model.uiState.value.selectedUserIds.isEmpty())
        assertTrue(model.uiState.value.selectionChanged)
    }

    @Test fun offlineEntryWaitsForConnectionAndLeavingStopsRecoveryQueries() = scenario {
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        model.enter(group()); yield()
        assertEquals(MemberSelectionStatus.WAITING_CONNECTION, model.uiState.value.status)
        assertFalse(model.uiState.value.hasSnapshot)
        assertTrue(repo.calls.isEmpty())
        phase(AuthPhase.CONNECTED)
        await { model.uiState.value.snapshotConfirmed }
        assertEquals(1, repo.calls.size)
        model.reset()
        auth.state.value = auth.state.value.copy(phase = AuthPhase.RECONNECTING); yield()
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED); yield()
        assertEquals(1, repo.calls.size)
        assertNull(model.uiState.value.group)
    }

    @Test fun obsoleteSuccessAndErrorAfterDisconnectCannotOverwriteRecoveryResult() = scenario {
        for (fails in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val before = repo.calls.size
            val finishedBefore = repo.finished
            repo.fetch = {
                withContext(NonCancellable) { gate.await() }
                if (fails) throw GroupFailure(GroupProblem.SERVER)
                snapshot(it, choices = listOf(member(99)))
            }
            model.enter(group()); await { repo.calls.size == before + 1 }
            phase(AuthPhase.RECONNECTING)
            repo.fetch = { snapshot(it, choices = listOf(member(10))) }
            phase(AuthPhase.CONNECTED)
            await { model.uiState.value.snapshotConfirmed }
            gate.complete(Unit)
            await { repo.finished == finishedBefore + 2 }
            assertEquals(listOf(10L), model.uiState.value.members.map { it.userId })
            assertNull(model.uiState.value.problem)
            model.reset()
        }
    }

    @Test fun groupChangeAndReentryDiscardOldResponsesAndSelections() = scenario {
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { withContext(NonCancellable) { gate.await() }; snapshot(it) }
        model.enter(group()); await { repo.calls.size == 1 }
        repo.fetch = { snapshot(it, choices = listOf(member(10))) }
        start(2); model.toggle(10)
        gate.complete(Unit); await { repo.finished == 2 }
        assertEquals(2L, model.uiState.value.group?.id)
        assertEquals(setOf(10L), model.uiState.value.selectedUserIds)
        model.reset(); start(2)
        assertTrue(model.uiState.value.selectedUserIds.isEmpty())
        assertEquals(listOf(1L, 2L, 2L), repo.calls)
    }

    @Test fun logoutCancelsPendingWorkAndAccountChangeClearsContentAndOldActions() = scenario {
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { withContext(NonCancellable) { gate.await() }; snapshot(it) }
        model.enter(group()); await { repo.calls.size == 1 }
        auth.logout(); await { model.uiState.value.group == null }
        model.enter(group()); model.refresh(); model.toggle(2); model.complete()
        gate.complete(Unit); await { repo.finished == 1 }
        assertFalse(model.uiState.value.hasSnapshot)
        assertFalse(model.uiState.value.showDeveloping)
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true)
        repo.fetch = { snapshot(it) }; start(); model.toggle(2)
        auth.state.value = auth.state.value.copy(user = AuthUser(8, "새 사용자"))
        model.complete(); await { model.uiState.value.group == null }
        assertTrue(model.uiState.value.selectedUserIds.isEmpty())
        assertFalse(model.uiState.value.showDeveloping)
    }

    companion object {
        private fun group(id: Long = 1) = Group(id, "이전 그룹명", "이전 설명", 99, 50, Membership.APPROVED)
        private fun member(id: Long, online: Boolean = true) = GroupMember(id, "같은 이름", GroupRole.MEMBER, online)
        // OWNER is deliberately neither first nor matched by nickname/order.
        private fun snapshot(id: Long = 1, choices: List<GroupMember> = listOf(member(2), member(3, false))) =
            GroupMembers(id, "서버 그룹", "", choices.size + 1,
                choices.take(1) + GroupMember(7, "같은 이름", GroupRole.OWNER, true) + choices.drop(1))
        private suspend fun await(predicate: () -> Boolean) { while (!predicate()) yield() }
    }
}
