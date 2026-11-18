package com.example.ridemate_api_test.app.session

import com.example.ridemate_api_test.core.datastore.TokenStore
import com.example.ridemate_api_test.core.network.AuthenticatedSession
import com.example.ridemate_api_test.core.network.UnauthorizedRequest
import com.example.ridemate_api_test.core.websocket.*
import com.example.ridemate_api_test.feature.auth.data.remote.AuthApi
import com.example.ridemate_api_test.feature.auth.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** App-owned session. Commands and event handling are serialized on the supplied main scope. */
class LoginSession(
    private val api: AuthApi,
    private val store: TokenStore,
    private val socket: LoginSocket,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val retryDelays: List<Long> = listOf(1_000, 2_000, 4_000),
) : AuthenticatedSession, LoginMessageChannel {
    private val mutableState = MutableStateFlow(AuthState())
    val state = mutableState.asStateFlow()
    private var credentials: AuthCredentials? = null
    private var generation = 0L
    private var connectionId = 0L
    private var connection: SocketConnection? = null
    private var loginJob: Job? = null
    private var expiryJob: Job? = null
    private var retryJob: Job? = null
    private var cleanupJob: Job? = null
    private var foreground = true
    private var retryCount = 0
    private val mutableMessages = MutableSharedFlow<LoginTextMessage>()
    override val messages = mutableMessages.asSharedFlow()
    override val sessionVersion: Long get() = generation
    override val identity: LoginSocketIdentity? get() = credentials?.takeIf {
        foreground && state.value.phase == AuthPhase.CONNECTED && it.expiresAtMillis > now()
    }?.let { LoginSocketIdentity(it.user.id, generation, connectionId) }

    override fun send(text: String): Boolean {
        if (identity == null || text.isBlank()) return false
        return connection?.send(text) == true
    }

    init {
        cleanupJob = scope.launch {
            val problem = clearStoredSession()
            mutableState.value = AuthState(phase = AuthPhase.SIGNED_OUT, problem = problem)
        }
    }

    fun login(loginId: String, password: String) {
        if (state.value.isBusy || credentials != null) return
        val ticket = ++generation
        mutableState.value = AuthState(phase = AuthPhase.AUTHENTICATING)
        loginJob = scope.launch {
            try {
                cleanupJob?.join()
                try { store.clear() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { throw AuthFailure(AuthProblem.STORAGE) }
                val result = api.login(loginId, password)
                ensureActive()
                if (ticket != generation) return@launch
                if (result.expiresAtMillis <= now()) throw AuthFailure(AuthProblem.EXPIRED)
                try { store.save(result) } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { throw AuthFailure(AuthProblem.STORAGE) }
                ensureActive()
                if (ticket != generation) return@launch
                credentials = result
                retryCount = 0
                mutableState.value = AuthState(phase = AuthPhase.CONNECTING, user = result.user)
                expiryJob = scope.launch {
                    delay((result.expiresAtMillis - now()).coerceAtLeast(0))
                    if (generation == ticket) endSession(AuthProblem.EXPIRED)
                }
                if (foreground) connect() else mutableState.value = state.value.copy(phase = AuthPhase.DISCONNECTED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (ticket == generation) {
                    credentials = null
                    mutableState.value = AuthState(phase = AuthPhase.SIGNED_OUT,
                        problem = (failure as? AuthFailure)?.problem ?: AuthProblem.NETWORK)
                    cleanupJob = scope.launch { clearStoredSession() }
                }
            }
        }
    }

    fun retryConnection() {
        if (credentials == null || state.value.isBusy || !foreground || state.value.phase == AuthPhase.CONNECTED) return
        retryCount = 0
        connect()
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (!value) {
            stopConnection()
            if (credentials != null) mutableState.value = state.value.copy(phase = AuthPhase.DISCONNECTED, problem = null)
        } else if (credentials != null) {
            retryCount = 0
            connect()
        }
    }

    fun logout() = endSession(null)

    override suspend fun <T> authenticated(request: suspend (String) -> T): T =
        withContext(scope.coroutineContext.minusKey(Job)) {
            val current = credentials ?: throw UnauthorizedRequest()
            if (!state.value.enteredSession) throw UnauthorizedRequest()
            if (current.expiresAtMillis <= now()) {
                endSession(AuthProblem.EXPIRED)
                throw UnauthorizedRequest()
            }
            val ticket = generation
            try {
                val result = request(current.accessToken)
                ensureActive()
                if (ticket != generation) throw CancellationException("Session changed")
                result
            } catch (failure: Exception) {
                ensureActive()
                if (ticket != generation) throw CancellationException("Session changed")
                if (failure is UnauthorizedRequest) endSession(AuthProblem.EXPIRED)
                throw failure
            }
        }

    private fun endSession(problem: AuthProblem?) {
        val ticket = ++generation
        loginJob?.cancel()
        expiryJob?.cancel()
        stopConnection()
        credentials = null
        mutableState.value = AuthState(phase = AuthPhase.SIGNED_OUT, problem = problem)
        val priorCleanup = cleanupJob
        cleanupJob = scope.launch {
            priorCleanup?.join()
            val storageProblem = clearStoredSession()
            if (generation == ticket && storageProblem != null) {
                mutableState.value = state.value.copy(problem = storageProblem)
            }
        }
    }

    private suspend fun clearStoredSession(): AuthProblem? = try {
        store.clear()
        null
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { AuthProblem.STORAGE }

    private fun stopConnection() {
        connectionId++ // Invalidate old callbacks before cancelling the transport.
        retryJob?.cancel()
        retryJob = null
        connection?.cancel()
        connection = null
    }

    private fun connect() {
        val current = credentials ?: return
        if (current.expiresAtMillis <= now()) { endSession(AuthProblem.EXPIRED); return }
        if (!foreground) return
        stopConnection()
        val ticket = generation
        val attempt = connectionId
        mutableState.value = state.value.copy(
            phase = if (state.value.enteredSession) AuthPhase.RECONNECTING else AuthPhase.CONNECTING, problem = null,
        )
        try {
            connection = socket.connect(current.accessToken) { event ->
                scope.launch {
                    if (generation != ticket || connectionId != attempt || !foreground) return@launch
                    when (event) {
                        SocketEvent.Open -> {
                            if ((credentials?.expiresAtMillis ?: 0) <= now()) {
                                endSession(AuthProblem.EXPIRED)
                                return@launch
                            }
                            retryCount = 0
                            mutableState.value = state.value.copy(phase = AuthPhase.CONNECTED, enteredSession = true, problem = null)
                        }
                        is SocketEvent.Failed -> onConnectionFailure(event.problem)
                        is SocketEvent.Text -> {
                            val currentIdentity = identity ?: return@launch
                            mutableMessages.emit(LoginTextMessage(currentIdentity, event.text))
                        }
                    }
                }
            }
        } catch (_: Exception) { onConnectionFailure(AuthProblem.NETWORK) }
    }

    private fun onConnectionFailure(problem: AuthProblem) {
        stopConnection()
        if (problem == AuthProblem.EXPIRED || problem == AuthProblem.FORBIDDEN) { endSession(problem); return }
        if (!foreground || credentials == null) return
        if (retryCount >= retryDelays.size) {
            mutableState.value = state.value.copy(phase = AuthPhase.DISCONNECTED, problem = problem)
            return
        }
        val wait = retryDelays[retryCount++]
        mutableState.value = state.value.copy(phase = AuthPhase.RECONNECTING, problem = problem)
        val ticket = generation
        retryJob = scope.launch {
            delay(wait)
            if (ticket == generation && foreground) connect()
        }
    }
}
