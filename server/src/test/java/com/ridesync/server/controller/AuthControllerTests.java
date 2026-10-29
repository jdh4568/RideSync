package com.ridesync.server.controller;

import com.ridesync.server.config.SecurityConfig;
import com.ridesync.server.entity.User;
import com.ridesync.server.repository.UserRepository;
import com.ridesync.server.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({AuthService.class, SecurityConfig.class})
class AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository repository;

    private static final String SIGNUP = """
            {"loginId":"rider","password":"1234","passwordConfirm":"1234","nickname":"라이더"}
            """;

    @Test
    void anonymousSignupWorksWithoutCsrfAndReturns201() throws Exception {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.nickname").value("라이더"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordConfirm").doesNotExist());
    }

    @Test
    void anonymousAvailabilityChecksWork() throws Exception {
        when(repository.existsByNickname("라이더")).thenReturn(true);
        mockMvc.perform(get("/api/v1/auth/check-login-id").param("loginId", "rider"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/v1/auth/check-nickname").param("nickname", "라이더"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void invalidQueryParametersNeverReachRepository() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-login-id").param("loginId", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/auth/check-nickname").param("nickname", "가".repeat(51)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/check-login-id"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void invalidSignupNeverReachesRepository() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateSignupReturns409() throws Exception {
        when(repository.existsByLoginId("rider")).thenReturn(true);
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    void passwordMismatchReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP.replace("\"passwordConfirm\":\"1234\"", "\"passwordConfirm\":\"wrong\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    void otherEndpointsRemainProtected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/home"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/auth/check-login-id").param("loginId", "rider"))
                .andExpect(status().isUnauthorized());
    }
}
