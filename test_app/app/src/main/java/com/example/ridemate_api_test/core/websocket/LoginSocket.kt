package com.example.ridemate_api_test.core.websocket

import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.feature.auth.domain.model.AuthProblem
import okhttp3.*
import okio.ByteString

sealed interface SocketEvent {
    data object Open : SocketEvent
    data class Failed(val problem: AuthProblem) : SocketEvent
    data class Text(val text: String) : SocketEvent
}
fun interface SocketConnection {
    fun cancel()
    fun send(text: String): Boolean = false
}
interface LoginSocket { fun connect(token: String, onEvent: (SocketEvent) -> Unit): SocketConnection }

class OkHttpLoginSocket(private val client: OkHttpClient, private val endpoint: ServerEndpoint) : LoginSocket {
    override fun connect(token: String, onEvent: (SocketEvent) -> Unit): SocketConnection {
        val request = Request.Builder().url(endpoint.url("/ws/login"))
            .header("Authorization", "Bearer $token").build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { onEvent(SocketEvent.Open) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(SocketEvent.Failed(when (response?.code) {
                    401 -> AuthProblem.EXPIRED
                    403 -> AuthProblem.FORBIDDEN
                    else -> AuthProblem.NETWORK
                }))
                response?.close()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                onEvent(SocketEvent.Failed(if (code == 1008) AuthProblem.EXPIRED else AuthProblem.NETWORK))
            }
            // Feature parsers decide which application messages are supported.
            override fun onMessage(webSocket: WebSocket, text: String) { onEvent(SocketEvent.Text(text)) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit
        })
        // Logout/background must not wait for the peer's close acknowledgement.
        return object : SocketConnection {
            override fun cancel() = socket.cancel()
            override fun send(text: String): Boolean = socket.send(text)
        }
    }
}
