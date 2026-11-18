package com.example.ridemate_api_test

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.feature.invitation.data.remote.*
import com.example.ridemate_api_test.feature.invitation.domain.model.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InvitationHttpTest {
    private val riding = """{"rideSessionId":10,"groupId":1,"status":"RIDING","startedAt":"2026-11-18T12:00:00"}"""
    private val cancelled = """{"invitationId":"inv","status":"CANCELLED"}"""
    private val success = """{"invitationId":"inv","groupId":1,"status":"PENDING","invitedCount":2}"""
    private val request = CreateInvitation(1, setOf(3, 2))
    private fun client() = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
        .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
    private fun close(client: OkHttpClient) {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
    private fun exchange(status: Int, body: String, length: Int? = null,
        block: suspend (HttpInvitationApi, () -> Pair<List<String>, String>) -> Unit) {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = client()
            val peer = executor.submit<Pair<List<String>, String>> {
                server.soTimeout = 3000
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; headers += line }
                    val count = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                    val chars = CharArray(count)
                    var offset = 0
                    while (offset < count) { val read = reader.read(chars, offset, count - offset); check(read > 0); offset += read }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\nContent-Length: ${length ?: bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                    headers to String(chars)
                }
            }
            try { runBlocking { withTimeout(5000) {
                block(HttpInvitationApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true)),
                    { peer.get(4, TimeUnit.SECONDS) })
            } } } finally { executor.shutdownNow(); close(client) }
        }
    }
    private suspend fun failure(problem: InvitationProblem, action: suspend () -> Unit) {
        try { action(); fail("Expected $problem") } catch (e: InvitationFailure) { assertEquals(problem, e.problem) }
    }

    @Test fun createsWithExactPostBearerAndIdsAndRequires201() = exchange(201, success) { api, peer ->
        val result = api.create("fixture-token", request)
        val (headers, raw) = peer()
        assertEquals("POST /api/v1/ride-invitations HTTP/1.1", headers.first())
        assertTrue(headers.contains("Authorization: Bearer fixture-token"))
        val json = JSONObject(raw)
        assertEquals(1, json.getInt("groupId"))
        assertEquals(listOf(2, 3), (0 until 2).map { json.getJSONArray("inviteeIds").getInt(it) })
        assertEquals(CreatedInvitation("inv", 1, 2), result)
    }

    @Test fun malformedSuccessOrWrongIdentityNeverBecomesConfirmedCreation() {
        for (body in listOf("{}", "[]", success + " garbage", success.replace("\"inv\"", "\" \""),
            success.replace("\"groupId\":1", "\"groupId\":2"), success.replace("\"groupId\":1", "\"groupId\":\"1\""),
            success.replace("\"invitedCount\":2", "\"invitedCount\":1"), success.replace("PENDING", "ACCEPTED"))) {
            exchange(201, body) { api, _ -> failure(InvitationProblem.INVALID_RESPONSE) { api.create("fixture-token", request) } }
        }
    }

    @Test fun distinguishesDefinitiveErrorsAndUnknownServerResults() {
        for ((status, code, problem) in listOf(
            Triple(400, "INVALID_REQUEST", InvitationProblem.INVALID_REQUEST), Triple(400, "INVALID_INVITEE", InvitationProblem.INVALID_INVITEE),
            Triple(403, "FORBIDDEN", InvitationProblem.FORBIDDEN), Triple(404, "GROUP_NOT_FOUND", InvitationProblem.GROUP_NOT_FOUND),
            Triple(409, "USER_OFFLINE", InvitationProblem.USER_OFFLINE), Triple(500, "SERVER_ERROR", InvitationProblem.SERVER),
            Triple(200, "", InvitationProblem.INVALID_RESPONSE), Triple(409, "OTHER", InvitationProblem.INVALID_RESPONSE),
        )) exchange(status, """{"code":"$code"}""") { api, _ ->
            failure(problem) { api.create("fixture-token", request) }
        }
    }

    @Test fun unauthorizedIsPropagatedToExistingSessionBoundaryEvenWithInvalidErrorBody() = exchange(401, "{") { api, _ ->
        try { api.create("fixture-token", request); fail() } catch (_: UnauthorizedRequest) { }
    }

    @Test fun brokenResponseIsUncertainNetworkFailure() = exchange(201, "{", length = 1000) { api, _ ->
        failure(InvitationProblem.NETWORK) { api.create("fixture-token", request) }
    }

    @Test fun startAndCancelUseExactPostPathsBearerAndEmptyBodies() {
        exchange(200, riding) { api, peer ->
            assertEquals(StartedRide(10, 1), api.start("fixture-token", "inv"))
            val (headers, raw) = peer()
            assertEquals("POST /api/v1/ride-invitations/inv/start HTTP/1.1", headers.first())
            assertTrue(headers.contains("Authorization: Bearer fixture-token"))
            assertEquals("", raw)
        }
        exchange(200, cancelled) { api, peer ->
            api.cancel("fixture-token", "inv")
            val (headers, raw) = peer()
            assertEquals("POST /api/v1/ride-invitations/inv/cancel HTTP/1.1", headers.first())
            assertTrue(headers.contains("Authorization: Bearer fixture-token"))
            assertEquals("", raw)
        }
    }

    @Test fun opaqueInvitationIdsAreEncodedAsASinglePathSegment() = exchange(200, riding) { api, peer ->
        api.start("fixture-token", "id/with?query")
        assertEquals("POST /api/v1/ride-invitations/id%2Fwith%3Fquery/start HTTP/1.1", peer().first.first())
    }

    @Test fun startAndCancelValidate200AndExactSuccessPayloads() {
        for (body in listOf("{}", riding + " trailing", riding.replace("RIDING", "PENDING"),
            riding.replace("\"rideSessionId\":10", "\"rideSessionId\":\"10\""),
            riding.replace("\"groupId\":1", "\"groupId\":0"), riding.replace("2026-11-18T12:00:00", "bad")))
            exchange(200, body) { api, _ -> failure(InvitationProblem.INVALID_RESPONSE) { api.start("fixture-token", "inv") } }
        for (body in listOf("{}", cancelled + " trailing", cancelled.replace("CANCELLED", "PENDING"), cancelled.replace("inv\"", "other\"")))
            exchange(200, body) { api, _ -> failure(InvitationProblem.INVALID_RESPONSE) { api.cancel("fixture-token", "inv") } }
        exchange(201, riding) { api, _ -> failure(InvitationProblem.INVALID_RESPONSE) { api.start("fixture-token", "inv") } }
        exchange(204, "") { api, _ -> failure(InvitationProblem.INVALID_RESPONSE) { api.cancel("fixture-token", "inv") } }
    }

    @Test fun actionErrorsDistinguish404ConflictsAndUnknownResults() {
        for ((http, code, problem) in listOf(
            Triple(400, "INVALID_REQUEST", InvitationProblem.INVALID_REQUEST),
            Triple(403, "FORBIDDEN", InvitationProblem.FORBIDDEN),
            Triple(404, "INVITATION_NOT_FOUND", InvitationProblem.INVITATION_NOT_FOUND),
            Triple(404, "GROUP_NOT_FOUND", InvitationProblem.GROUP_NOT_FOUND),
            Triple(409, "INSUFFICIENT_PARTICIPANTS", InvitationProblem.INSUFFICIENT_PARTICIPANTS),
            Triple(409, "INVALID_PARTICIPANT", InvitationProblem.INVALID_PARTICIPANT),
            Triple(500, "SERVER_ERROR", InvitationProblem.SERVER),
            Triple(409, "UNKNOWN", InvitationProblem.INVALID_RESPONSE),
        )) for (start in listOf(true, false)) exchange(http, """{"code":"$code"}""") { api, _ ->
            failure(problem) { if (start) api.start("fixture-token", "inv") else api.cancel("fixture-token", "inv") }
        }
    }

    @Test fun action401AndBrokenSuccessKeepExistingAuthAndUncertainBoundaries() {
        for (start in listOf(true, false)) {
            exchange(401, "{") { api, _ ->
                try { if (start) api.start("fixture-token", "inv") else api.cancel("fixture-token", "inv"); fail() }
                catch (_: UnauthorizedRequest) { }
            }
            exchange(200, "{", length = 1000) { api, _ ->
                failure(InvitationProblem.NETWORK) { if (start) api.start("fixture-token", "inv") else api.cancel("fixture-token", "inv") }
            }
        }
    }

    @Test fun cancellationClosesTheSinglePostWithoutResending() {
        for (operation in listOf("create", "start", "cancel")) cancellationClosesPost(operation)
    }

    private fun cancellationClosesPost(operation: String) {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = client()
            val accepted = CompletableDeferred<Unit>()
            val peer = executor.submit<Int> {
                server.soTimeout = 3000
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; headers += line }
                    val count = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                    repeat(count) { reader.read() }
                    accepted.complete(Unit)
                    reader.read()
                }
            }
            try {
                runBlocking { withTimeout(5000) {
                    val api = HttpInvitationApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true))
                    val job = async { when (operation) {
                        "start" -> api.start("fixture-token", "inv")
                        "cancel" -> api.cancel("fixture-token", "inv")
                        else -> api.create("fixture-token", request)
                    } }
                    accepted.await()
                    job.cancelAndJoin()
                } }
                assertEquals(-1, peer.get(4, TimeUnit.SECONDS).toInt())
            } finally { executor.shutdownNow(); close(client) }
        }
    }
}
