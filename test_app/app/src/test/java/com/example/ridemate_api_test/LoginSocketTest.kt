package com.example.ridemate_api_test

import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.core.websocket.*
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class LoginSocketTest {
    @Test fun forwardsTextSendsOnTheSameSocketAndAnswersServerPing() = runBlocking {
        ServerSocket(0).use { server ->
            val client = OkHttpClient()
            val opened = CompletableDeferred<Unit>()
            val sendReady = CompletableDeferred<Unit>()
            val receivedText = CompletableDeferred<String>()
            val exchange = async(Dispatchers.IO) {
                server.soTimeout = 4_000
                server.accept().use { peer ->
                    peer.soTimeout = 4_000
                    val input = peer.getInputStream()
                    val reader = input.bufferedReader(Charsets.US_ASCII)
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: error("Missing upgrade request")
                        if (line.isEmpty()) break
                        lines += line
                    }
                    assertEquals("GET /ws/login HTTP/1.1", lines.first())
                    assertTrue(lines.any { it.equals("Authorization: Bearer fixture-token", ignoreCase = true) })
                    val key = lines.first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
                    val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
                    val output = peer.getOutputStream()
                    output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    output.flush()
                    opened.await()
                    // Transport forwards text; the feature parser ignores malformed JSON.
                    output.write(byteArrayOf(0x81.toByte(), 1, '{'.code.toByte()))
                    output.write(byteArrayOf(0x82.toByte(), 1, 65)) // Binary is ignored.
                    output.write(byteArrayOf(0x89.toByte(), 2, 79, 75))
                    output.flush()
                    assertEquals(0x8A, input.read()) // Pong
                    val length = input.read()
                    assertEquals(0x82, length) // Masked two-byte payload
                    val mask = ByteArray(4).also { bytes -> repeat(4) { bytes[it] = input.read().toByte() } }
                    val payload = ByteArray(2) { (input.read() xor mask[it].toInt()).toByte() }
                    assertArrayEquals(byteArrayOf(79, 75), payload)
                    sendReady.complete(Unit)
                    assertEquals(0x81, input.read())
                    assertEquals(0x87, input.read())
                    val textMask = ByteArray(4).also { bytes -> repeat(4) { bytes[it] = input.read().toByte() } }
                    val textPayload = ByteArray(7) { (input.read() xor textMask[it % 4].toInt()).toByte() }
                    assertEquals("fixture", String(textPayload, Charsets.UTF_8))
                }
            }
            val link = OkHttpLoginSocket(client, ServerEndpoint("http://127.0.0.1:${server.localPort}", true))
                .connect("fixture-token") { event ->
                    if (event == SocketEvent.Open) opened.complete(Unit)
                    if (event is SocketEvent.Text) receivedText.complete(event.text)
                }
            try { withTimeout(5_000) {
                sendReady.await()
                assertEquals("{", receivedText.await())
                assertTrue(link.send("fixture"))
                exchange.await()
            } }
            finally { link.cancel(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
        }
    }
}
