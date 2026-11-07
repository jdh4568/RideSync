package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.data.remote.readLoginBody
import com.example.ridemate_api_test.feature.auth.domain.model.AuthFailure
import com.example.ridemate_api_test.feature.auth.domain.model.AuthProblem
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginResponseBodyTest {
    @Test fun truncatedBodyIsNetworkFailure() = checkIncompleteBody(keepConnectionOpen = false)

    @Test fun bodyReadTimeoutIsNetworkFailure() = checkIncompleteBody(keepConnectionOpen = true)

    private fun checkIncompleteBody(keepConnectionOpen: Boolean) {
        ServerSocket(0).use { server ->
            val releasePeer = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            val client = OkHttpClient.Builder()
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build()
            val peerTask = executor.submit {
                server.soTimeout = 3_000
                server.accept().use { peer ->
                    peer.soTimeout = 3_000
                    val reader = peer.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (true) {
                        val line = reader.readLine() ?: error("Missing request headers")
                        if (line.isEmpty()) break
                    }
                    // Headers succeed, but the declared body is never fully delivered.
                    peer.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n" +
                            "Content-Type: application/json\r\nConnection: close\r\n\r\n{")
                            .toByteArray(Charsets.US_ASCII))
                        flush()
                    }
                    if (keepConnectionOpen) check(releasePeer.await(5, TimeUnit.SECONDS))
                }
            }
            try {
                val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/api/v1/auth/login").build()
                client.newCall(request).execute().use { response ->
                    assertEquals(200, response.code)
                    val failure = assertThrows(AuthFailure::class.java) { response.readLoginBody() }
                    assertEquals(AuthProblem.NETWORK, failure.problem)
                }
            } finally {
                releasePeer.countDown()
                try { peerTask.get(4, TimeUnit.SECONDS) }
                finally {
                    executor.shutdownNow()
                    client.dispatcher.executorService.shutdownNow()
                    client.connectionPool.evictAll()
                }
            }
        }
    }
}
