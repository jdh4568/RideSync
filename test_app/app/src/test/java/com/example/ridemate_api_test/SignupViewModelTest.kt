package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.presentation.signup.*
import com.example.ridemate_api_test.feature.auth.domain.repository.*
import com.example.ridemate_api_test.feature.auth.data.remote.signupProblem
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SignupViewModelTest {
    private class Repo : SignupRepository {
        var checkCalls = 0
        var signupCalls = 0
        var checkBlock: suspend (SignupIdentity, String) -> Boolean = { _, _ -> true }
        var signupBlock: suspend (SignupRequest) -> SignupResult = { SignupResult(1, it.nickname) }
        override suspend fun check(identity: SignupIdentity, value: String): Boolean {
            checkCalls++
            return checkBlock(identity, value)
        }
        override suspend fun signup(request: SignupRequest): SignupResult {
            signupCalls++
            return signupBlock(request)
        }
    }
    private fun scenario(block: suspend CoroutineScope.(Repo, SignupViewModel) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repo()
        val model = SignupViewModel(repo, scope)
        try { withTimeout(3000) { block(repo, model) } }
        finally { model.reset(); scope.cancel() }
    }
    private fun fill(model: SignupViewModel, password: String = "test") {
        model.update(SignupField.LOGIN_ID, "test")
        model.update(SignupField.NICKNAME, "테스트")
        model.update(SignupField.PASSWORD, password)
        model.update(SignupField.PASSWORD_CONFIRM, password)
    }
    private fun checkBoth(model: SignupViewModel) {
        model.checkAvailability(SignupField.LOGIN_ID)
        model.checkAvailability(SignupField.NICKNAME)
    }

    @Test fun validationBoundariesAndNoTrim() = scenario { repo, model ->
        model.submit()
        assertEquals(4, model.uiState.value.errors.size)
        model.update(SignupField.LOGIN_ID, "a".repeat(50))
        model.checkAvailability(SignupField.LOGIN_ID)
        assertEquals(1, repo.checkCalls)
        model.update(SignupField.LOGIN_ID, "a".repeat(50) + " ")
        model.checkAvailability(SignupField.LOGIN_ID)
        assertEquals(SignupError.TOO_LONG, model.uiState.value.errors[SignupField.LOGIN_ID])
        assertEquals(1, repo.checkCalls)
        model.update(SignupField.NICKNAME, "  ")
        assertEquals(SignupError.REQUIRED, model.uiState.value.errors[SignupField.NICKNAME])
        model.update(SignupField.NICKNAME, "가".repeat(51))
        assertEquals(SignupError.TOO_LONG, model.uiState.value.errors[SignupField.NICKNAME])
    }

    @Test fun passwordUtf8BoundaryAndMismatch() = scenario { _, model ->
        fill(model, "가".repeat(24))
        checkBoth(model)
        assertTrue(model.uiState.value.canSubmit)
        model.update(SignupField.PASSWORD, "가".repeat(24) + "a")
        assertEquals(SignupError.PASSWORD_TOO_LONG, model.uiState.value.errors[SignupField.PASSWORD])
        assertEquals(SignupError.PASSWORD_MISMATCH, model.uiState.value.errors[SignupField.PASSWORD_CONFIRM])
        assertFalse(model.uiState.value.canSubmit)
        model.update(SignupField.PASSWORD, "가".repeat(24))
        assertTrue(model.uiState.value.canSubmit)
    }

    @Test fun requiresBothChecksAndInvalidatesOnEdit() = scenario { repo, model ->
        fill(model)
        model.submit()
        assertEquals(0, repo.signupCalls)
        model.checkAvailability(SignupField.LOGIN_ID)
        assertFalse(model.uiState.value.canSubmit)
        model.checkAvailability(SignupField.NICKNAME)
        assertTrue(model.uiState.value.canSubmit)
        model.update(SignupField.LOGIN_ID, "changed")
        model.update(SignupField.LOGIN_ID, "test")
        assertFalse(model.uiState.value.canSubmit)
    }

    @Test fun lateResponseCannotValidateChangedThenRestoredValue() = scenario { repo, model ->
        val old = CompletableDeferred<Boolean>()
        repo.checkBlock = { _, _ -> withContext(NonCancellable) { old.await() } }
        fill(model)
        model.checkAvailability(SignupField.LOGIN_ID)
        model.checkAvailability(SignupField.LOGIN_ID)
        assertEquals(1, repo.checkCalls)
        model.update(SignupField.LOGIN_ID, "other")
        model.update(SignupField.LOGIN_ID, "test")
        repo.checkBlock = { _, _ -> false }
        model.checkAvailability(SignupField.LOGIN_ID)
        old.complete(true)
        yield()
        assertEquals(Availability.TAKEN, model.uiState.value.checks[SignupField.LOGIN_ID]?.status)
    }

    @Test fun resetDiscardsLateCheckAndSecrets() = scenario { repo, model ->
        val gate = CompletableDeferred<Boolean>()
        repo.checkBlock = { _, _ -> withContext(NonCancellable) { gate.await() } }
        fill(model)
        model.checkAvailability(SignupField.NICKNAME)
        model.reset()
        gate.complete(true)
        yield()
        assertEquals(SignupUiState(), model.uiState.value)
    }

    @Test fun checkFailureCanBeRetried() = scenario { repo, model ->
        fill(model)
        repo.checkBlock = { _, _ -> throw SignupFailure(SignupProblem.NETWORK) }
        model.checkAvailability(SignupField.LOGIN_ID)
        assertEquals(Availability.ERROR, model.uiState.value.checks[SignupField.LOGIN_ID]?.status)
        repo.checkBlock = { _, _ -> true }
        model.checkAvailability(SignupField.LOGIN_ID)
        assertEquals(Availability.AVAILABLE, model.uiState.value.checks[SignupField.LOGIN_ID]?.status)
    }

    @Test fun doubleSubmitLocksInputAndSuccessClearsPasswords() = scenario { repo, model ->
        val gate = CompletableDeferred<Unit>()
        repo.signupBlock = { gate.await(); SignupResult(1, it.nickname) }
        fill(model)
        checkBoth(model)
        model.submit()
        model.submit()
        model.update(SignupField.LOGIN_ID, "changed")
        assertEquals("test", model.uiState.value.loginId)
        assertTrue(model.uiState.value.isSubmitting)
        assertEquals(1, repo.signupCalls)
        gate.complete(Unit)
        assertFalse(model.uiState.value.isSubmitting)
        assertEquals("test", model.uiState.value.completedLoginId)
        assertEquals("", model.uiState.value.password)
        assertEquals("", model.uiState.value.passwordConfirm)
        model.submit()
        assertEquals(1, repo.signupCalls)
        model.reset()
        assertNull(model.uiState.value.completedLoginId)
    }

    @Test fun duplicateErrorsInvalidateRelevantChecks() = scenario { repo, model ->
        for (problem in listOf(SignupProblem.DUPLICATE_LOGIN_ID, SignupProblem.DUPLICATE_NICKNAME, SignupProblem.DUPLICATE_USER)) {
            model.reset(); fill(model); checkBoth(model)
            repo.signupBlock = { throw SignupFailure(problem) }
            model.submit()
            assertFalse(model.uiState.value.isSubmitting)
            assertFalse(model.uiState.value.canSubmit)
            assertEquals(problem, model.uiState.value.problem)
            if (problem == SignupProblem.DUPLICATE_USER) assertTrue(model.uiState.value.checks.isEmpty())
            else assertEquals(Availability.TAKEN, model.uiState.value.checks[
                if (problem == SignupProblem.DUPLICATE_LOGIN_ID) SignupField.LOGIN_ID else SignupField.NICKNAME]?.status)
        }
    }

    @Test fun ambiguousFailureNeverRetriesAndRequiresFreshChecks() = scenario { repo, model ->
        fill(model); checkBoth(model)
        repo.signupBlock = { throw SignupFailure(SignupProblem.NETWORK) }
        model.submit(); model.submit()
        assertEquals(1, repo.signupCalls)
        assertTrue(model.uiState.value.outcomeUncertain)
        assertEquals("test", model.uiState.value.password)
        assertFalse(model.uiState.value.isSubmitting)
        assertFalse(model.uiState.value.canSubmit)
        assertNull(model.uiState.value.completedLoginId)
    }

    @Test fun lateSignupCannotNavigateAfterReset() = scenario { repo, model ->
        val gate = CompletableDeferred<Unit>()
        repo.signupBlock = { withContext(NonCancellable) { gate.await(); SignupResult(1, it.nickname) } }
        fill(model); checkBoth(model); model.submit()
        model.reset()
        gate.complete(Unit)
        yield()
        assertEquals(SignupUiState(), model.uiState.value)
    }

    @Test fun statusAndCodeMustBothMatch() {
        assertEquals(SignupProblem.DUPLICATE_LOGIN_ID, signupProblem(409, "DUPLICATE_LOGIN_ID"))
        assertEquals(SignupProblem.INVALID_RESPONSE, signupProblem(200, "DUPLICATE_LOGIN_ID"))
        assertEquals(SignupProblem.PASSWORD_MISMATCH, signupProblem(400, "PASSWORD_MISMATCH"))
        assertEquals(SignupProblem.INVALID_PASSWORD, signupProblem(400, "INVALID_PASSWORD"))
        assertEquals(SignupProblem.SERVER, signupProblem(503, ""))
        assertEquals(SignupProblem.INVALID_RESPONSE, signupProblem(409, "unknown"))
    }
}

