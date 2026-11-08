package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.presentation.signup.*
import org.junit.Assert.*
import org.junit.Test

class SignupViewModelTest {
    @Test fun blankFieldsAreRejected() {
        val model = SignupViewModel()
        model.submit()
        assertEquals(4, model.uiState.value.errors.size)
        assertFalse(model.uiState.value.showUnavailableNotice)
    }

    @Test fun nameLengthBoundaryAndNoTrimming() {
        val model = SignupViewModel()
        model.update(SignupField.LOGIN_ID, "a".repeat(50))
        model.checkAvailability(SignupField.LOGIN_ID)
        assertTrue(model.uiState.value.showUnavailableNotice)
        model.update(SignupField.LOGIN_ID, "a".repeat(50) + " ")
        model.checkAvailability(SignupField.LOGIN_ID)
        assertEquals(SignupError.TOO_LONG, model.uiState.value.errors[SignupField.LOGIN_ID])
        model.update(SignupField.NICKNAME, " ".repeat(3))
        model.checkAvailability(SignupField.NICKNAME)
        assertEquals(SignupError.REQUIRED, model.uiState.value.errors[SignupField.NICKNAME])
        model.update(SignupField.NICKNAME, "가".repeat(51))
        model.checkAvailability(SignupField.NICKNAME)
        assertEquals(SignupError.TOO_LONG, model.uiState.value.errors[SignupField.NICKNAME])
    }

    @Test fun passwordLimitIsUtf8Bytes() {
        val model = validForm("가".repeat(24))
        model.submit()
        assertTrue(model.uiState.value.errors.isEmpty())
        model.update(SignupField.PASSWORD, "가".repeat(24) + "a")
        model.update(SignupField.PASSWORD_CONFIRM, "가".repeat(24) + "a")
        model.submit()
        assertEquals(SignupError.PASSWORD_TOO_LONG, model.uiState.value.errors[SignupField.PASSWORD])
    }

    @Test fun mismatchRevalidatesAfterPasswordChanges() {
        val model = validForm("test")
        model.update(SignupField.PASSWORD_CONFIRM, "different")
        model.submit()
        assertEquals(SignupError.PASSWORD_MISMATCH, model.uiState.value.errors[SignupField.PASSWORD_CONFIRM])
        model.update(SignupField.PASSWORD, "different")
        model.submit()
        assertTrue(model.uiState.value.errors.isEmpty())
    }

    @Test fun validFormOnlyShowsUnavailableAndExitClearsSecrets() {
        val model = validForm("test")
        repeat(2) { model.submit() }
        assertTrue(model.uiState.value.showUnavailableNotice)
        model.reset()
        assertEquals(SignupUiState(), model.uiState.value)
    }

    private fun validForm(password: String) = SignupViewModel().apply {
        update(SignupField.LOGIN_ID, "test")
        update(SignupField.NICKNAME, "테스트")
        update(SignupField.PASSWORD, password)
        update(SignupField.PASSWORD_CONFIRM, password)
    }
}

