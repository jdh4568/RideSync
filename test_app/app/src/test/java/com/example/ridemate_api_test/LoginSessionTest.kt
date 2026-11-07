package com.example.ridemate_api_test

import com.example.ridemate_api_test.app.session.LoginSession
import com.example.ridemate_api_test.core.datastore.TokenStore
import com.example.ridemate_api_test.core.websocket.*
import com.example.ridemate_api_test.feature.auth.data.remote.AuthApi
import com.example.ridemate_api_test.feature.auth.domain.model.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LoginSessionTest {
    private class Store : TokenStore {
        var value: AuthCredentials? = null
        var failSave = false
        var failClear = false
        override suspend fun save(credentials: AuthCredentials) {
            if (failSave) error("Storage unavailable")
            value = credentials
        }
        override suspend fun clear() {
            if (failClear) error("Storage unavailable")
            value = null
        }
    }
    private class Api : AuthApi {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var problem: AuthProblem? = null
        var expiresAt = System.currentTimeMillis() + 60_000
        override suspend fun login(loginId: String, password: String): AuthCredentials {
            calls++
            gate?.await()
            problem?.let { throw AuthFailure(it) }
            return AuthCredentials("test-token", AuthUser(7, "테스트"), expiresAt)
        }
    }
    private class Sockets(private val store: Store) : LoginSocket {
        val connections = mutableListOf<Link>()
        class Link(val notify: (SocketEvent) -> Unit) : SocketConnection {
            var cancelled = false
            override fun cancel() { cancelled = true }
        }
        override fun connect(token: String, onEvent: (SocketEvent) -> Unit): SocketConnection {
            check(store.value != null) // Persistence must finish before handshake starts.
            return Link(onEvent).also { connections += it }
        }
    }
    private class Fixture(val scope: CoroutineScope) {
        val api = Api()
        val store = Store()
        val sockets = Sockets(store)
        val session = LoginSession(api, store, sockets, scope, retryDelays = listOf(5, 10))
        suspend fun ready() = await { session.state.value.phase == AuthPhase.SIGNED_OUT }
        suspend fun start() {
            ready()
            session.login("test", "password")
            await { sockets.connections.isNotEmpty() }
        }
    }
    private fun scenario(block: suspend Fixture.() -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                try { withTimeout(5_000) { Fixture(scope).block() } } finally { scope.cancel() }
            }
        }
    }

    @Test fun loginWaitsForSocketAndIgnoresOldCallbacksAfterLogout() = scenario {
        ready()
        session.login("test", "password")
        session.login("test", "password")
        await { sockets.connections.size == 1 }
        assertEquals(1, api.calls)
        assertFalse(session.state.value.enteredSession)
        val old = sockets.connections.single()
        old.notify(SocketEvent.Open)
        await { session.state.value.enteredSession }
        session.logout()
        old.notify(SocketEvent.Open)
        await { store.value == null }
        yield()
        assertTrue(old.cancelled)
        assertEquals(AuthPhase.SIGNED_OUT, session.state.value.phase)
        assertNull(session.state.value.user)
    }

    @Test fun boundedReconnectDoesNotRepeatLoginPost() = scenario {
        start()
        for (i in 0..2) {
            await { sockets.connections.size == i + 1 }
            sockets.connections[i].notify(SocketEvent.Failed(AuthProblem.NETWORK))
        }
        await { session.state.value.phase == AuthPhase.DISCONNECTED }
        assertFalse(session.state.value.enteredSession)
        delay(40)
        assertEquals(3, sockets.connections.size)
        assertEquals(1, api.calls)
        session.retryConnection()
        assertEquals(4, sockets.connections.size)
        sockets.connections.last().notify(SocketEvent.Open)
        await { session.state.value.phase == AuthPhase.CONNECTED }
    }

    @Test fun backgroundStopsSocketAndForegroundReauthenticatesConnection() = scenario {
        start()
        val old = sockets.connections.single()
        old.notify(SocketEvent.Open)
        await { session.state.value.enteredSession }
        session.setForeground(false)
        old.notify(SocketEvent.Failed(AuthProblem.NETWORK))
        delay(40)
        assertTrue(old.cancelled)
        assertEquals(1, sockets.connections.size)
        session.setForeground(true)
        assertEquals(2, sockets.connections.size)
        assertEquals(1, api.calls)
        assertTrue(session.state.value.enteredSession)
    }

    @Test fun rejectedWebSocketClearsCredentialsAndDoesNotRetry() = scenario {
        start()
        sockets.connections.single().notify(SocketEvent.Failed(AuthProblem.EXPIRED))
        await { session.state.value.phase == AuthPhase.SIGNED_OUT && store.value == null }
        delay(40)
        assertEquals(AuthProblem.EXPIRED, session.state.value.problem)
        assertEquals(1, sockets.connections.size)
    }

    @Test fun failedStorageNeverConnects() = scenario {
        ready()
        store.failSave = true
        session.login("test", "password")
        await { session.state.value.problem == AuthProblem.STORAGE }
        assertTrue(sockets.connections.isEmpty())
        assertNull(store.value)
    }

    @Test fun logoutCancelsPendingLoginWithoutResurrectingSession() = scenario {
        ready()
        api.gate = CompletableDeferred()
        session.login("test", "password")
        await { api.calls == 1 }
        session.logout()
        api.gate!!.complete(Unit)
        delay(30)
        assertNull(store.value)
        assertTrue(sockets.connections.isEmpty())
        assertEquals(AuthPhase.SIGNED_OUT, session.state.value.phase)
    }

    @Test fun expiryClosesConnectedSession() = scenario {
        ready()
        api.expiresAt = System.currentTimeMillis() + 120
        session.login("test", "password")
        await { sockets.connections.isNotEmpty() }
        sockets.connections.single().notify(SocketEvent.Open)
        await { session.state.value.problem == AuthProblem.EXPIRED && store.value == null }
        assertTrue(sockets.connections.single().cancelled)
        assertFalse(session.state.value.enteredSession)
    }

    @Test fun invalidCredentialsStayOnLoginScreen() = scenario {
        ready()
        api.problem = AuthProblem.INVALID_CREDENTIALS
        session.login("test", "wrong")
        await { session.state.value.problem == AuthProblem.INVALID_CREDENTIALS }
        assertFalse(session.state.value.enteredSession)
        assertTrue(sockets.connections.isEmpty())
    }

    companion object {
        private suspend fun await(predicate: () -> Boolean) {
            withTimeout(3_000) { while (!predicate()) delay(2) }
        }
    }
}
