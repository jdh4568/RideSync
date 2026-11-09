package com.example.ridemate_api_test.feature.auth.presentation.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SignupViewModel(private val repository: SignupRepository, private val testScope: CoroutineScope? = null) : ViewModel() {
    private val workScope get() = testScope ?: viewModelScope
    private val mutableState = MutableStateFlow(SignupUiState())
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    private val revisions = mutableMapOf<SignupField, Long>()
    private val checks = mutableMapOf<SignupField, Job>()
    private var submitJob: Job? = null

    fun update(field: SignupField, value: String) {
        val state = uiState.value
        if (state.isSubmitting || state.completedLoginId != null || state.value(field) == value) return
        revisions[field] = (revisions[field] ?: 0) + 1
        checks.remove(field)?.cancel()
        val next = when (field) {
            SignupField.LOGIN_ID -> state.copy(loginId = value)
            SignupField.PASSWORD -> state.copy(password = value)
            SignupField.PASSWORD_CONFIRM -> state.copy(passwordConfirm = value)
            SignupField.NICKNAME -> state.copy(nickname = value)
        }
        var errors = state.errors - field
        validate(next, field)?.let { errors = errors + (field to it) }
        if (field == SignupField.PASSWORD && next.passwordConfirm.isNotEmpty()) {
            errors = errors - SignupField.PASSWORD_CONFIRM
            validate(next, SignupField.PASSWORD_CONFIRM)?.let { errors = errors + (SignupField.PASSWORD_CONFIRM to it) }
        }
        mutableState.value = next.copy(errors = errors, checks = state.checks - field)
    }

    fun checkAvailability(field: SignupField) {
        val state = uiState.value
        if (field != SignupField.LOGIN_ID && field != SignupField.NICKNAME) return
        if (state.isSubmitting || state.completedLoginId != null || state.checks[field]?.status == Availability.CHECKING) return
        val error = validate(state, field)
        mutableState.value = state.copy(errors = if (error == null) state.errors - field else state.errors + (field to error))
        if (error != null) return
        val value = state.value(field)
        val epoch = generation
        val revision = (revisions[field] ?: 0) + 1
        revisions[field] = revision
        mutableState.update { it.copy(checks = it.checks + (field to AvailabilityState(Availability.CHECKING))) }
        checks[field] = workScope.launch {
            val result = try {
                val available = repository.check(
                    if (field == SignupField.LOGIN_ID) SignupIdentity.LOGIN_ID else SignupIdentity.NICKNAME, value)
                AvailabilityState(if (available) Availability.AVAILABLE else Availability.TAKEN)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                AvailabilityState(Availability.ERROR, (failure as? SignupFailure)?.problem ?: SignupProblem.NETWORK)
            }
            if (epoch == generation && revisions[field] == revision && uiState.value.value(field) == value)
                mutableState.update { it.copy(checks = it.checks + (field to result)) }
        }
    }

    fun submit() {
        val state = uiState.value
        if (state.isSubmitting || state.completedLoginId != null) return
        val errors = SignupField.entries.mapNotNull { field -> validate(state, field)?.let { field to it } }.toMap()
        mutableState.value = state.copy(errors = errors)
        if (!uiState.value.canSubmit) return
        val epoch = generation
        mutableState.update { it.copy(isSubmitting = true, problem = null, outcomeUncertain = false) }
        submitJob = workScope.launch {
            try {
                repository.signup(SignupRequest(state.loginId, state.password, state.passwordConfirm, state.nickname))
                ensureActive()
                if (generation == epoch) mutableState.value = SignupUiState(completedLoginId = state.loginId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (generation != epoch) return@launch
                val problem = (failure as? SignupFailure)?.problem ?: SignupProblem.NETWORK
                val uncertain = problem in setOf(SignupProblem.NETWORK, SignupProblem.SERVER, SignupProblem.INVALID_RESPONSE)
                mutableState.update { current ->
                    var checks = current.checks
                    var fieldErrors = current.errors
                    when (problem) {
                        SignupProblem.DUPLICATE_LOGIN_ID -> checks = checks + (SignupField.LOGIN_ID to AvailabilityState(Availability.TAKEN))
                        SignupProblem.DUPLICATE_NICKNAME -> checks = checks + (SignupField.NICKNAME to AvailabilityState(Availability.TAKEN))
                        SignupProblem.DUPLICATE_USER -> checks = emptyMap()
                        SignupProblem.PASSWORD_MISMATCH -> fieldErrors = fieldErrors + (SignupField.PASSWORD_CONFIRM to SignupError.PASSWORD_MISMATCH)
                        SignupProblem.INVALID_PASSWORD -> fieldErrors = fieldErrors + (SignupField.PASSWORD to SignupError.PASSWORD_TOO_LONG)
                        else -> Unit
                    }
                    // Require explicit fresh checks after an ambiguous outcome; never resend automatically.
                    if (uncertain) checks = emptyMap()
                    current.copy(isSubmitting = false, checks = checks, errors = fieldErrors,
                        problem = problem, outcomeUncertain = uncertain)
                }
            }
        }
    }

    fun reset() {
        generation++
        checks.values.forEach { it.cancel() }
        checks.clear()
        revisions.clear()
        submitJob?.cancel()
        submitJob = null
        mutableState.value = SignupUiState()
    }

    override fun onCleared() { reset() }
}

private fun SignupUiState.value(field: SignupField): String = when (field) {
    SignupField.LOGIN_ID -> loginId
    SignupField.PASSWORD -> password
    SignupField.PASSWORD_CONFIRM -> passwordConfirm
    SignupField.NICKNAME -> nickname
}

internal fun validate(state: SignupUiState, field: SignupField): SignupError? {
    val value = state.value(field)
    return when {
        value.isBlank() -> SignupError.REQUIRED
        (field == SignupField.LOGIN_ID || field == SignupField.NICKNAME) && value.length > 50 -> SignupError.TOO_LONG
        field == SignupField.PASSWORD && value.toByteArray(Charsets.UTF_8).size > 72 -> SignupError.PASSWORD_TOO_LONG
        field == SignupField.PASSWORD_CONFIRM && value != state.password -> SignupError.PASSWORD_MISMATCH
        else -> null
    }
}

