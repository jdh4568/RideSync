package com.example.ridemate_api_test

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.feature.group.data.remote.HttpGroupApi
import com.example.ridemate_api_test.feature.group.data.repository.GroupRepositoryImpl
import com.example.ridemate_api_test.feature.group.domain.model.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/** Run tools/test-group-join-requests.ps1: uses the IDE's existing org.json runtime. */
class GroupJoinRequestsHttpTest {
    private val first = """{"groupMemberId":15,"groupId":1,"groupName":"그룹","userId":5,"nickname":"신청자","status":"PENDING"}"""
    private val second = """{"groupMemberId":18,"groupId":3,"groupName":"다른 그룹","userId":8,"nickname":"신청자2","status":"PENDING"}"""

    @Test fun listUsesBearerGetAndPreservesServerOrderAcrossGroups() = exchange(200, "[$second,$first]") { api, headers ->
        val result = api.joinRequests("contract-token")
        assertEquals(listOf(18L, 15L), result.map { it.id })
        val request = headers()
        assertEquals("GET /api/v1/groups/join-requests HTTP/1.1", request.first())
        assertTrue(request.contains("Authorization: Bearer contract-token"))
    }

    @Test fun successfulEmptyArrayIsAccepted() = exchange(200, "[]") { api, _ ->
        assertTrue(api.joinRequests("contract-token").isEmpty())
    }

    @Test fun approveAndRejectValidateTargetAndResultAndUseEmptyPost() {
        for (decision in GroupRequestDecision.entries) exchange(200,
            """{"groupId":1,"userId":5,"status":"${decision.status.name}"}""") { api, headers ->
            val result = api.respondToJoinRequest("contract-token", 1, 5, decision)
            assertEquals(GroupRequestResult(1, 5, decision.status), result)
            val request = headers()
            assertEquals("POST /api/v1/groups/1/requests/5/${decision.path} HTTP/1.1", request.first())
            assertTrue(request.contains("Authorization: Bearer contract-token"))
            assertTrue(request.contains("Content-Length: 0"))
        }
    }

    @Test fun malformedNonPendingDuplicateAndWrongTypeListsAreRejected() {
        for (body in listOf("{}", "[$first,$first]", "[${first.replace("PENDING", "APPROVED")}]",
            "[${first.replace("\"groupMemberId\":15", "\"groupMemberId\":\"15\"")}]",
            "[${first.replace("\"userId\":5", "\"userId\":0")}]", "[${first.replace("신청자", " ")}]",
            "[$first,${first.replace("\"groupMemberId\":15", "\"groupMemberId\":16")}]")
        ) exchange(200, body) { api, _ ->
            failure(GroupProblem.INVALID_RESPONSE) { api.joinRequests("contract-token") }
        }
    }

    @Test fun mismatchedMutationTargetsStatusAndBrokenBodyAreNotSuccess() {
        for (body in listOf("""{"groupId":2,"userId":5,"status":"APPROVED"}""",
            """{"groupId":1,"userId":9,"status":"APPROVED"}""",
            """{"groupId":1,"userId":5,"status":"REJECTED"}""", "{", "")) exchange(200, body) { api, _ ->
            failure(GroupProblem.INVALID_RESPONSE) { api.respondToJoinRequest("contract-token", 1, 5, GroupRequestDecision.APPROVE) }
        }
    }

    @Test fun forbiddenAndServerErrorsRemainFailures() {
        for ((status, problem) in listOf(403 to GroupProblem.FORBIDDEN, 500 to GroupProblem.SERVER))
            exchange(status, """{"code":"FORBIDDEN","message":"error"}""") { api, _ ->
                failure(problem) { api.joinRequests("contract-token") }
            }
    }

    @Test fun fullConflictAndNotFoundRemainMutationFailures() {
        for ((code, status, problem) in listOf(Triple("GROUP_FULL", 409, GroupProblem.FULL),
            Triple("CONFLICT", 409, GroupProblem.CONFLICT), Triple("NOT_FOUND", 404, GroupProblem.NOT_FOUND),
            Triple("FORBIDDEN", 403, GroupProblem.FORBIDDEN))) exchange(status, """{"code":"$code"}""") { api, _ ->
            failure(problem) { api.respondToJoinRequest("contract-token", 1, 5, GroupRequestDecision.APPROVE) }
        }
    }

    @Test fun unauthorizedEvenWithoutBodyUsesAuthenticatedSessionFailurePath() = exchange(401, "") { api, _ ->
        var unauthorized = false
        val session = object : AuthenticatedSession {
            override suspend fun <T> authenticated(request: suspend (String) -> T): T = try { request("contract-token") }
            catch (failure: UnauthorizedRequest) { unauthorized = true; throw failure }
        }
        failure(GroupProblem.UNAUTHORIZED) { GroupRepositoryImpl(api, session).joinRequests() }
        assertTrue(unauthorized)
    }

    @Test fun repositoryUsesExistingAuthenticatedSessionForBothDecisions() {
        for (decision in GroupRequestDecision.entries) exchange(200,
            """{"groupId":1,"userId":5,"status":"${decision.status.name}"}""") { api, headers ->
            var calls = 0
            val session = object : AuthenticatedSession {
                override suspend fun <T> authenticated(request: suspend (String) -> T): T { calls++; return request("existing-token") }
            }
            GroupRepositoryImpl(api, session).respondToJoinRequest(1, 5, decision)
            assertEquals(1, calls); assertTrue(headers().contains("Authorization: Bearer existing-token"))
        }
    }

    @Test fun truncatedMutationResponseIsNetworkFailure() = exchange(200, "{", declaredLength = 100) { api, _ ->
        failure(GroupProblem.NETWORK) { api.respondToJoinRequest("contract-token", 1, 5, GroupRequestDecision.APPROVE) }
    }

    @Test fun unexpectedSuccessStatusIsNotAccepted() = exchange(204, "") { api, _ ->
        failure(GroupProblem.INVALID_RESPONSE) { api.joinRequests("contract-token") }
    }

    private suspend fun failure(expected: GroupProblem, action: suspend () -> Any) {
        try { action(); fail("Expected $expected") }
        catch (failure: GroupFailure) { assertEquals(expected, failure.problem) }
    }

    private fun exchange(status: Int, body: String, declaredLength: Int? = null,
        block: suspend (HttpGroupApi, () -> List<String>) -> Unit) {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
                .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
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
                runBlocking { block(HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true)),
                    { peer.get(4, TimeUnit.SECONDS) }) }
            } finally {
                executor.shutdownNow(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll()
            }
        }
    }
}
