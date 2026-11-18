package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import com.example.ridemate_api_test.feature.group.presentation.selection.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class GroupSelectionViewModelTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true))
        override fun login(loginId: String, password: String) = Unit
        override fun retryConnection() = Unit
        override fun logout() { state.value = AuthState(phase = AuthPhase.SIGNED_OUT) }
    }
    private class Repository : GroupRepository {
        override suspend fun members(groupId: Long): GroupMembers = error("Not used")
        var calls = 0
        var finished = 0
        var fetch: suspend () -> List<Group> = { groupsForTest() }
        override suspend fun ownedGroups(): List<Group> {
            calls++
            try { return fetch() } finally { finished++ }
        }
        override suspend fun groups(): List<Group> = error("Must query owned groups only")
        override suspend fun join(groupId: Long) = error("No mutation")
        override suspend fun create(request: CreateGroup): CreatedGroup = error("No mutation")
        override suspend fun joinRequests(): List<GroupJoinRequest> = error("Not used")
        override suspend fun respondToJoinRequest(groupId: Long, userId: Long, decision: GroupRequestDecision): GroupRequestResult = error("No mutation")
    }
    private class Fixture(scope: CoroutineScope) {
        val auth = Auth()
        val repo = Repository()
        val model = GroupSelectionViewModel(repo, auth, scope)
        suspend fun start() {
            model.enter()
            await { model.uiState.value.status == GroupSelectionStatus.CONTENT }
        }
    }
    private fun scenario(block: suspend Fixture.() -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                try { withTimeout(5000) { Fixture(scope).block() } } finally { scope.cancel() }
            }
        }
    }

    @Test fun repeatedEntryAndRetryDuringLoadingUseOneOwnedQuery() = scenario {
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { gate.await(); groupsForTest() }
        model.enter(); model.enter(); model.retry(); model.select(1); model.openMembers()
        await { repo.calls == 1 }
        assertEquals(GroupSelectionStatus.LOADING, model.uiState.value.status)
        assertFalse(model.uiState.value.canComplete)
        gate.complete(Unit)
        await { model.uiState.value.status == GroupSelectionStatus.CONTENT }
        assertNull(model.uiState.value.selectedGroupId)
        model.enter(); model.retry()
        auth.state.value = auth.state.value.copy(phase = AuthPhase.RECONNECTING)
        yield()
        assertEquals(1, repo.calls)
    }

    @Test fun actualListHandoffBlocksDuplicateNavigationAndReturnKeepsSelection() = scenario {
        start()
        model.openMembers()
        assertFalse(model.uiState.value.memberSelectionOpen)
        model.select(1); model.select(2); model.select(999)
        assertEquals(2L, model.uiState.value.selectedGroupId)
        model.openMembers(); model.openMembers(); model.select(1)
        assertTrue(model.uiState.value.memberSelectionOpen)
        assertEquals(2L, model.uiState.value.selectedGroupId)
        model.returnFromMembers()
        assertTrue(model.uiState.value.canComplete)
        assertEquals(1, repo.calls)
    }

    @Test fun failuresAreNotEmptySuccessAndExplicitRetryIsDeduplicated() = scenario {
        for (problem in listOf(GroupProblem.NETWORK, GroupProblem.FORBIDDEN, GroupProblem.SERVER, GroupProblem.INVALID_RESPONSE)) {
            repo.fetch = { throw GroupFailure(problem) }
            model.enter()
            await { model.uiState.value.status == GroupSelectionStatus.ERROR }
            assertEquals(problem, model.uiState.value.problem)
            assertFalse(model.uiState.value.canComplete)
            val before = repo.calls
            val gate = CompletableDeferred<Unit>()
            repo.fetch = { gate.await(); emptyList() }
            model.retry(); model.retry()
            await { repo.calls == before + 1 }
            assertEquals(GroupSelectionStatus.LOADING, model.uiState.value.status)
            assertNull(model.uiState.value.problem)
            gate.complete(Unit)
            await { model.uiState.value.status == GroupSelectionStatus.CONTENT }
            assertTrue(model.uiState.value.groups.isEmpty())
            assertFalse(model.uiState.value.canComplete)
            model.reset()
        }
    }

    @Test fun memberRoundTripKeepsOwnedListAndSelectionButHomeExitResetsTheFlow() = scenario {
        start(); model.select(2)
        val original = model.uiState.value.groups
        assertEquals(2L, model.openMembers()?.id)
        assertNull(model.openMembers())
        assertEquals(2L, model.groupForMembers()?.id)
        model.returnFromMembers(); model.enter()
        assertEquals(original, model.uiState.value.groups)
        assertEquals(2L, model.uiState.value.selectedGroupId)
        assertEquals(1, repo.calls)
        assertNull(model.groupForMembers())
        model.reset(); start()
        assertNull(model.uiState.value.selectedGroupId)
        assertEquals(2, repo.calls)
    }

    @Test fun leavingCancelsRequestAndReentryStartsWithNoSelection() = scenario {
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { gate.await(); groupsForTest() }
        model.enter()
        await { repo.calls == 1 }
        model.reset()
        await { repo.finished == 1 }
        assertTrue(gate.isActive)
        repo.fetch = { groupsForTest() }
        start(); model.select(1); model.openMembers()
        model.reset(); start()
        assertEquals(3, repo.calls)
        assertNull(model.uiState.value.selectedGroupId)
        assertFalse(model.uiState.value.memberSelectionOpen)
    }

    @Test fun lateSuccessAndLateErrorCannotOverwriteAReenteredScreen() = scenario {
        for (fails in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val before = repo.finished
            repo.fetch = {
                withContext(NonCancellable) { gate.await() }
                if (fails) throw GroupFailure(GroupProblem.SERVER)
                groupsForTest()
            }
            val callsBefore = repo.calls
            model.enter()
            await { repo.calls == callsBefore + 1 }
            model.reset()
            repo.fetch = { emptyList() }
            start()
            gate.complete(Unit)
            await { repo.finished == before + 2 }
            assertEquals(GroupSelectionStatus.CONTENT, model.uiState.value.status)
            assertTrue(model.uiState.value.groups.isEmpty())
            assertNull(model.uiState.value.problem)
            model.reset()
        }
    }

    @Test fun logoutAndAccountChangeClearContentAndBlockStaleActions() = scenario {
        start(); model.select(1); model.openMembers()
        auth.logout()
        model.select(2); model.openMembers(); model.retry(); model.enter()
        await { model.uiState.value.groups.isEmpty() }
        assertNull(model.uiState.value.selectedGroupId)
        assertFalse(model.uiState.value.memberSelectionOpen)
        assertEquals(1, repo.calls)
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(8, "다른 사용자"), true)
        start(); model.select(2)
        auth.state.value = auth.state.value.copy(user = AuthUser(9, "새 사용자"))
        model.openMembers()
        await { model.uiState.value.groups.isEmpty() }
        assertFalse(model.uiState.value.memberSelectionOpen)
        start()
        assertNull(model.uiState.value.selectedGroupId)
        assertEquals(3, repo.calls)
    }

    @Test fun sessionChangeDuringRequestDiscardsItsLateResult() = scenario {
        val gate = CompletableDeferred<Unit>()
        repo.fetch = { withContext(NonCancellable) { gate.await() }; groupsForTest() }
        model.enter()
        await { repo.calls == 1 }
        auth.logout()
        await { model.uiState.value.status == GroupSelectionStatus.LOADING && model.uiState.value.groups.isEmpty() }
        yield()
        gate.complete(Unit)
        await { repo.finished == 1 }
        assertTrue(model.uiState.value.groups.isEmpty())
        assertFalse(model.uiState.value.canComplete)
    }

    companion object {
        private fun groupsForTest() = listOf(Group(1, "첫 그룹", "", 1, 2, Membership.APPROVED),
            Group(2, "정원 그룹", "설명", 2, 2, Membership.APPROVED))
        private suspend fun await(predicate: () -> Boolean) { while (!predicate()) yield() }
    }
}
