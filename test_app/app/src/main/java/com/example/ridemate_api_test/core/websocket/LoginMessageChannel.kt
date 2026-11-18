package com.example.ridemate_api_test.core.websocket

import kotlinx.coroutines.flow.SharedFlow

data class LoginSocketIdentity(val userId: Long, val sessionVersion: Long, val connectionVersion: Long)
data class LoginTextMessage(val identity: LoginSocketIdentity, val text: String)

/** App-owned transport boundary. send success is not server acknowledgement. */
interface LoginMessageChannel {
    val sessionVersion: Long
    val identity: LoginSocketIdentity?
    val messages: SharedFlow<LoginTextMessage>
    fun send(text: String): Boolean
}
