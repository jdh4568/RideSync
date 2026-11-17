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

/** Local HTTP contract tests. No real server accounts or API mutation. */
class GroupSelectionHttpTest {
    private val group = """{"groupId":1,"groupName":"한강 모임","description":"설명","memberCount":13,"maxMembers":20,"membershipStatus":"APPROVED","myRole":"OWNER"}"""

    @Test fun ownedQueryUsesExistingSessionBearerGetAndPreservesFieldsAndOrder() =
        exchange(200, "[$group,${group.replace("\"groupId\":1", "\"groupId\":3").replace("\"설명\"", "null")}]") { api, headers ->
            var calls = 0
            val session = object : AuthenticatedSession {
                override suspend fun <T> authenticated(request: suspend (String) -> T): T {
                    calls++
                    return request("existing-token")
                }
            }
            val groups = GroupRepositoryImpl(api, session).ownedGroups()
            assertEquals(1, calls)
            assertEquals(listOf(1L, 3L), groups.map { it.id })
            assertEquals("한강 모임", groups.first().name)
            assertEquals("설명", groups.first().description)
            assertEquals("", groups.last().description)
            assertEquals(13, groups.first().memberCount)
            assertEquals(20, groups.first().maxMembers)
            assertEquals(Membership.APPROVED, groups.first().membership)
            assertEquals("GET /api/v1/groups/owned HTTP/1.1", headers().first())
            assertTrue(headers().contains("Authorization: Bearer existing-token"))
        }

    @Test fun successfulEmptyArrayIsAnEmptyList() = exchange(200, "[]") { api, _ ->
        assertTrue(api.ownedGroups("contract-token").isEmpty())
    }

    @Test fun brokenOrUnauthorizedChoiceDataIsRejectedInsteadOfFiltered() {
        for (body in listOf("{}", "{", "", "[$group,$group]",
            "[${group.replace("APPROVED", "PENDING")}]", "[${group.replace("OWNER", "MEMBER")}]",
            "[${group.replace(",\"myRole\":\"OWNER\"", "")}]",
            "[${group.replace("\"groupId\":1", "\"groupId\":\"1\"")}]",
            "[${group.replace("한강 모임", " ")}]")) {
            exchange(200, body) { api, _ -> failure(GroupProblem.INVALID_RESPONSE) { api.ownedGroups("contract-token") } }
        }
    }

    @Test fun allGroupQueryStillAcceptsNonOwnerAndNullableOrUnknownMembership() {
        for ((value, expected) in listOf("null" to Membership.NONE, "\"PENDING\"" to Membership.PENDING,
            "\"REJECTED\"" to Membership.REJECTED, "\"FUTURE\"" to Membership.UNKNOWN)) {
            val body = group.replace("\"APPROVED\"", value).replace(",\"myRole\":\"OWNER\"", "")
            exchange(200, "[$body]") { api, headers ->
                assertEquals(expected, api.groups("contract-token").single().membership)
                assertEquals("GET /api/v1/groups HTTP/1.1", headers().first())
            }
        }
    }

    @Test fun unauthorizedEvenWithBrokenBodyReachesTheSessionBoundary() = exchange(401, "{") { api, _ ->
        var unauthorized = false
        val session = object : AuthenticatedSession {
            override suspend fun <T> authenticated(request: suspend (String) -> T): T = try { request("contract-token") }
            catch (failure: UnauthorizedRequest) { unauthorized = true; throw failure }
        }
        failure(GroupProblem.UNAUTHORIZED) { GroupRepositoryImpl(api, session).ownedGroups() }
        assertTrue(unauthorized)
    }

    @Test fun errorsAndUnexpectedStatusesAreNotSuccessfulEmptyLists() {
        for ((status, problem) in listOf(403 to GroupProblem.FORBIDDEN, 500 to GroupProblem.SERVER,
            404 to GroupProblem.NOT_FOUND, 204 to GroupProblem.INVALID_RESPONSE)) {
            exchange(status, "") { api, _ -> failure(problem) { api.ownedGroups("contract-token") } }
        }
    }

    @Test fun interruptedResponseIsNetworkFailure() = exchange(200, "[", declaredLength = 100) { api, _ ->
        failure(GroupProblem.NETWORK) { api.ownedGroups("contract-token") }
    }

    @Test fun cancellingTheQueryClosesThePendingHttpCall() {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = client()
            val accepted = CompletableDeferred<Unit>()
            val peer = executor.submit<Int> {
                server.soTimeout = 3000
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    accepted.complete(Unit)
                    reader.read()
                }
            }
            try {
                runBlocking {
                    withTimeout(5000) {
                        val api = HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true))
                        val request = async { api.ownedGroups("contract-token") }
                        accepted.await()
                        request.cancelAndJoin()
                        assertTrue(request.isCancelled)
                    }
                }
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
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
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
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\nContent-Length: ${declaredLength ?: bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                    lines
                }
            }
            try {
                runBlocking { withTimeout(5000) {
                    block(HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true)),
                        { peer.get(4, TimeUnit.SECONDS) })
                } }
            } finally { executor.shutdownNow(); close(client) }
        }
    }
}
