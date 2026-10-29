package com.ridesync.server.service;

import com.ridesync.server.dto.SignupRequest;
import com.ridesync.server.entity.User;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.UserRepository;
import com.ridesync.server.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTests {
    private UserRepository repository;
    private AuthService service;
    private SignupRequest request;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        service = new AuthService(repository, encoder, mock(JwtTokenProvider.class));
        request = new SignupRequest();
        request.setLoginId("rider");
        request.setNickname("라이더");
        request.setPassword("1234");
        request.setPasswordConfirm("1234");
    }

    @Test
    void signupHashesPasswordAndSetsCreationTime() {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals("rider", user.getLoginId());
            assertEquals("라이더", user.getNickname());
            assertNotEquals("1234", user.getPassword());
            assertTrue(encoder.matches("1234", user.getPassword()));
            assertNotNull(user.getCreatedAt());
            user.setId(1L);
            return user;
        });
        var response = service.signup(request);
        assertEquals(1L, response.getUserId());
        assertEquals("라이더", response.getNickname());
    }

    @Test
    void passwordMismatchDoesNotAccessDatabase() {
        request.setPasswordConfirm("different");
        assertEquals("PASSWORD_MISMATCH", assertThrows(ApiException.class, () -> service.signup(request)).getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void passwordLimitUsesUtf8Bytes() {
        request.setPassword("가".repeat(25));
        request.setPasswordConfirm(request.getPassword());
        assertEquals("INVALID_PASSWORD", assertThrows(ApiException.class, () -> service.signup(request)).getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateLoginIdPreventsSave() {
        when(repository.existsByLoginId("rider")).thenReturn(true);
        assertEquals("DUPLICATE_LOGIN_ID", assertThrows(ApiException.class, () -> service.signup(request)).getCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateNicknamePreventsSave() {
        when(repository.existsByNickname("라이더")).thenReturn(true);
        assertEquals("DUPLICATE_NICKNAME", assertThrows(ApiException.class, () -> service.signup(request)).getCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateIsConflict() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate", new SQLException("duplicate key", "23000", 1062)));
        ApiException error = assertThrows(ApiException.class, () -> service.signup(request));
        assertEquals(409, error.getStatus().value());
        assertEquals("DUPLICATE_USER", error.getCode());
    }

    @Test
    void otherDatabaseErrorsAreNotMisreportedAsDuplicates() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "null", new SQLException("not null", "23000", 1048)));
        assertThrows(DataIntegrityViolationException.class, () -> service.signup(request));
    }

    @Test
    void availabilityReflectsExistingAccounts() {
        assertTrue(service.checkLoginId("rider").isAvailable());
        assertTrue(service.checkNickname("라이더").isAvailable());
        when(repository.existsByLoginId("rider")).thenReturn(true);
        when(repository.existsByNickname("라이더")).thenReturn(true);
        assertFalse(service.checkLoginId("rider").isAvailable());
        assertFalse(service.checkNickname("라이더").isAvailable());
    }
}
