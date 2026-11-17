package com.example.ridemate_api_test

// Extra JVM contract tests use only org.json classes from the installed Android Studio layoutlib.
// Run with Verify-GroupHttp.ps1; the normal JVM task remains independent of Android Studio paths.
import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.feature.group.data.remote.*
import com.example.ridemate_api_test.feature.group.data.repository.GroupRepositoryImpl
import com.example.ridemate_api_test.feature.group.domain.model.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroupHttpContractTest {
    private val row = """{"groupId":1,"groupName":"한강","description":null,"memberCount":1,"maxMembers":2,"membershipStatus":null,"myRole":null}"""

    @Test fun listUsesBearerAndMapsNullableAndUnknownMembershipWithoutRoleInference() {
        exchange(200, "[$row]") { api ->
            val group = api.groups("test-token").single()
            assertEquals("한강", group.name); assertEquals("", group.description)
            assertEquals(Membership.NONE, group.membership)
        }.also {
            assertEquals("GET /api/v1/groups HTTP/1.1", it.first.first())
            assertTrue(it.first.any { header -> header == "Authorization: Bearer test-token" })
        }
        exchange(200, "[${row.replace("\"membershipStatus\":null", "\"membershipStatus\":\"FUTURE\"")}]") {
            assertEquals(Membership.UNKNOWN, it.groups("test-token").single().membership)
        }
        exchange(200, "[]") { assertTrue(it.groups("test-token").isEmpty()) }
    }

    @Test fun malformedOrPartialListsNeverBecomeEmptySuccess() {
        listOf("{}", "[$row,$row]", "[${row.replace("\"groupId\":1", "\"groupId\":\"1\"")}]",
            "[${row.replace("\"memberCount\":1", "\"memberCount\":-1")}]",
            "[${row.replace("\"membershipStatus\":null,", "")}]", "[").forEach { body ->
            exchange(200, body) { api -> expect(GroupProblem.INVALID_RESPONSE) { api.groups("test-token") } }
        }
    }

    @Test fun joinHasEmptyBodyAndRequiresTheMatchingPendingResponse() {
        val request = exchange(200, """{"groupId":4,"status":"PENDING"}""") { it.join("test-token", 4) }
        assertEquals("POST /api/v1/groups/4/join HTTP/1.1", request.first.first())
        assertEquals("", request.second)
        exchange(200, """{"groupId":9,"status":"PENDING"}""") { api ->
            expect(GroupProblem.INVALID_RESPONSE) { api.join("test-token", 4) }
        }
    }

    @Test fun createSendsExactJsonAndAcceptsOnlyCreatedResponse() {
        val request = exchange(201, """{"groupId":8,"groupName":"한강 모임"}""") { api ->
            assertEquals(CreatedGroup(8, "한강 모임"), api.create("test-token", CreateGroup("한강 모임", "주말\n라이딩", 20)))
        }
        assertEquals("POST /api/v1/groups HTTP/1.1", request.first.first())
        val json = JSONObject(request.second)
        assertEquals(3, json.length()); assertEquals("한강 모임", json.getString("groupName"))
        assertEquals("주말\n라이딩", json.getString("description")); assertEquals(20, json.getInt("maxMembers"))
        exchange(200, """{"groupId":8,"groupName":"한강 모임"}""") { api ->
            expect(GroupProblem.INVALID_RESPONSE) { api.create("test-token", CreateGroup("한강 모임", "", 2)) }
        }
    }

    @Test fun httpFailuresUseStatusFallbackAndSpecificConflictCodes() {
        listOf(400 to GroupProblem.INVALID_REQUEST, 403 to GroupProblem.FORBIDDEN,
            404 to GroupProblem.NOT_FOUND, 409 to GroupProblem.CONFLICT, 500 to GroupProblem.SERVER,
            204 to GroupProblem.INVALID_RESPONSE).forEach { (status, problem) ->
            exchange(status, if (status == 204) "" else "not-json") { api -> expect(problem) { api.groups("test-token") } }
        }
        exchange(409, """{"code":"GROUP_FULL","message":"full"}""") { api ->
            expect(GroupProblem.FULL) { api.join("test-token", 1) }
        }
        exchange(401, "not-json") { api ->
            val session = object : AuthenticatedSession {
                override suspend fun <T> authenticated(request: suspend (String) -> T) = request("test-token")
            }
            expect(GroupProblem.UNAUTHORIZED) { GroupRepositoryImpl(api, session).groups() }
        }
    }

    @Test fun truncatedResponseIsNetworkFailureAndDoesNotRetryPost() {
        exchange(200, "{", claimedLength = 100) { api ->
            expect(GroupProblem.NETWORK) { api.join("test-token", 1) }
        }
    }

    private suspend fun expect(problem: GroupProblem, block: suspend () -> Any?) {
        try { block(); fail("Expected $problem") }
        catch (failure: GroupFailure) { assertEquals(problem, failure.problem) }
    }

    private fun exchange(status: Int, body: String, claimedLength: Int? = null,
        action: suspend (GroupApi) -> Unit): Pair<List<String>, String> {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
                .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
            val peer = executor.submit<Pair<List<String>, String>> {
                server.soTimeout = 4000
                server.accept().use { socket ->
                    socket.soTimeout = 4000
                    val input = socket.getInputStream().buffered()
                    fun line(): String {
                        val bytes = mutableListOf<Byte>()
                        while (true) { val next = input.read(); if (next == -1 || next == 10) break; if (next != 13) bytes += next.toByte() }
                        return bytes.toByteArray().toString(Charsets.UTF_8)
                    }
                    val headers = mutableListOf<String>()
                    while (true) { val line = line(); if (line.isEmpty()) break; headers += line }
                    val length = headers.find { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                    val raw = input.readNBytes(length).toString(Charsets.UTF_8)
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().write("HTTP/1.1 $status Response\r\nContent-Length: ${claimedLength ?: bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    socket.getOutputStream().write(bytes); socket.getOutputStream().flush()
                    headers to raw
                }
            }
            try {
                runBlocking { action(HttpGroupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true))) }
                return peer.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll()
            }
        }
    }
}
