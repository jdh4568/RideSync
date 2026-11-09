package com.example.ridemate_api_test

import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.feature.auth.data.remote.HttpSignupApi
import com.example.ridemate_api_test.feature.auth.domain.repository.*
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class SignupHttpTest {
    @Test fun publicCheckEncodesQueryAndHasNoAuthorization() {
        exchange("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n",
            SignupProblem.SERVER) { headers ->
            assertTrue(headers.first().startsWith("GET /api/v1/auth/check-nickname?nickname="))
            val query = headers.first().substringAfter("nickname=").substringBefore(" HTTP/")
            assertEquals("한 글+&?", URLDecoder.decode(query, "UTF-8"))
            assertFalse(headers.any { it.startsWith("Authorization:", ignoreCase = true) })
        }
    }

    @Test fun incompleteSuccessfulBodyIsNetworkError() {
        exchange("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n{", SignupProblem.NETWORK)
    }

    @Test fun unexpectedSuccessStatusIsNotAccepted() {
        exchange("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n", SignupProblem.INVALID_RESPONSE)
    }

    private fun exchange(response: String, expected: SignupProblem, inspect: (List<String>) -> Unit = {}) {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
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
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    lines
                }
            }
            try {
                val api = HttpSignupApi(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true))
                val failure = try {
                    runBlocking { api.check(SignupIdentity.NICKNAME, "한 글+&?") }
                    fail("Expected failure")
                    null
                } catch (failure: SignupFailure) { failure }
                assertEquals(expected, failure?.problem)
                inspect(peer.get(4, TimeUnit.SECONDS))
            } finally {
                executor.shutdownNow()
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }
}

