package com.example.ridemate_api_test

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.feature.group.data.remote.HttpGroupApi
import com.example.ridemate_api_test.feature.group.data.repository.GroupRepositoryImpl
import com.example.ridemate_api_test.feature.group.domain.model.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/** Local HTTP fixtures only; these are not real Spring server results. */
class MemberSelectionHttpTest {
    private val owner = """{"userId":7,"nickname":"같은 이름","role":"OWNER","online":true}"""
    private val member = """{"userId":2,"nickname":"같은 이름","role":"MEMBER","online":false}"""
    private val body = """{"groupId":1,"groupName":"서버 그룹","description":"설명","memberCount":2,"members":[$member,$owner]}"""

    @Test fun queryUsesExistingSessionBearerGetAndReadsTheObjectWithOwnerAndSnapshot() = exchange(200, body) { api, headers ->
        var calls = 0
        val session = object : AuthenticatedSession {
            override suspend fun <T> authenticated(request: suspend (String) -> T): T { calls++; return request("existing-token") }
        }
        val result = GroupRepositoryImpl(api, session).members(1)
        assertEquals(1, calls)
        assertEquals(1L, result.groupId)
        assertEquals("서버 그룹", result.groupName)
        assertEquals("설명", result.description)
        assertEquals(2, result.memberCount)
        assertEquals(listOf(2L, 7L), result.members.map { it.userId })
        assertFalse(result.members.first().online)
        assertEquals(GroupRole.MEMBER, result.members.first().role)
        assertEquals(GroupRole.OWNER, result.members.last().role)
        assertEquals("GET /api/v1/groups/1/members HTTP/1.1", headers().first())
        assertTrue(headers().contains("Authorization: Bearer existing-token"))
    }

    @Test fun ownerOnlyGroupAndNullableDescriptionAreParsedWithoutInventingInvitees() =
        exchange(200, body.replace("\"description\":\"설명\"", "\"description\":null")
            .replace("\"memberCount\":2", "\"memberCount\":1").replace("$member,", "")) { api, _ ->
            val result = api.members("contract-token", 1)
            assertEquals("", result.description)
            assertEquals(1, result.memberCount)
            assertEquals(7L, result.members.single().userId)
        }

    @Test fun malformedIdentityRoleTypesAndCountAreRejectedInsteadOfSuccess() {
        val broken = listOf("[]", "{}", "{", "", body.replace("\"groupId\":1", "\"groupId\":2"),
            body.replace("\"groupId\":1", "\"groupId\":\"1\""),
            body.replace("\"userId\":2", "\"userId\":0"), body.replace("\"userId\":2", "\"userId\":7"),
            body.replace("\"userId\":2", "\"userId\":2.5"), body.replace("MEMBER", "FUTURE"),
            body.replace("\"online\":false", "\"online\":\"false\""),
            body.replace("\"online\":false", "\"online\":0"), body.replace("\"online\":false", "\"online\":null"),
            body.replace(",\"online\":false", ""), body.replace("같은 이름", " "),
            body.replace("서버 그룹", " "), body.replace("\"memberCount\":2", "\"memberCount\":1"),
            body.replace("\"memberCount\":2", "\"memberCount\":\"2\""),
            body.replace("\"description\":\"설명\",", ""))
        for (invalid in broken) exchange(200, invalid) { api, _ ->
            failure(GroupProblem.INVALID_RESPONSE) { api.members("contract-token", 1) }
        }
    }

    @Test fun unauthorizedReachesTheExistingSessionBoundaryEvenWithMalformedBody() = exchange(401, "{") { api, _ ->
        var unauthorized = false
        val session = object : AuthenticatedSession {
            override suspend fun <T> authenticated(request: suspend (String) -> T): T = try { request("contract-token") }
            catch (failure: UnauthorizedRequest) { unauthorized = true; throw failure }
        }
        failure(GroupProblem.UNAUTHORIZED) { GroupRepositoryImpl(api, session).members(1) }
        assertTrue(unauthorized)
    }

    @Test fun failedAndUnexpectedStatusesAreNotAnEmptyGroup() {
        for ((status, problem) in listOf(403 to GroupProblem.FORBIDDEN, 404 to GroupProblem.NOT_FOUND,
            500 to GroupProblem.SERVER, 204 to GroupProblem.INVALID_RESPONSE)) {
            exchange(status, "") { api, _ -> failure(problem) { api.members("contract-token", 1) } }
        }
    }

    @Test fun interruptedResponseIsNetworkFailure() = exchange(200, "{", declaredLength = 100) { api, _ ->
        failure(GroupProblem.NETWORK) { api.members("contract-token", 1) }
    }

    @Test fun nonPositiveGroupIdsNeverIssueHttpRequests() {
        val client = client()
        try {
            val api = HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:1", true))
            runBlocking {
                for (id in listOf(0L, -1L)) {
                    try { api.members("contract-token", id); fail("Invalid group ID") }
                    catch (_: IllegalArgumentException) { /* rejected before HTTP */ }
                }
            }
        } finally { close(client) }
    }

    @Test fun cancellationClosesThePendingMembersHttpCall() {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = client()
            val accepted = CompletableDeferred<Unit>()
            val peer = executor.submit<Int> {
                server.soTimeout = 3000
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* headers */ }
                    accepted.complete(Unit)
                    reader.read()
                }
            }
            try {
                runBlocking { withTimeout(5000) {
                    val api = HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true))
                    val request = async { api.members("contract-token", 1) }
                    accepted.await(); request.cancelAndJoin()
                    assertTrue(request.isCancelled)
                } }
                assertEquals(-1, peer.get(4, TimeUnit.SECONDS).toInt())
            } finally { executor.shutdownNow(); close(client) }
        }
    }

    private suspend fun failure(expected: GroupProblem, action: suspend () -> Any) {
        try { action(); fail("Expected $expected") }
        catch (failure: GroupFailure) { assertEquals(expected, failure.problem) }
    }
    private fun client() = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
        .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
    private fun close(client: OkHttpClient) {
        client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll()
    }
    private fun exchange(status: Int, body: String, declaredLength: Int? = null,
        block: suspend (HttpGroupApi, () -> List<String>) -> Unit) {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = client()
            val peer = executor.submit<List<String>> {
                server.soTimeout = 3000
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; lines += line }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\nContent-Length: ${declaredLength ?: bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                    lines
                }
            }
            try { runBlocking { withTimeout(5000) {
                block(HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true)),
                    { peer.get(4, TimeUnit.SECONDS) })
            } } } finally { executor.shutdownNow(); close(client) }
        }
    }
}
