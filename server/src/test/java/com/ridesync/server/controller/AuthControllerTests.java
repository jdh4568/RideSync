package com.ridesync.server.controller;

import com.ridesync.server.config.SecurityConfig;
import com.ridesync.server.entity.User;
import com.ridesync.server.repository.UserRepository;
import com.ridesync.server.service.AuthService;
import com.ridesync.server.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({AuthService.class, SecurityConfig.class, JwtTokenProvider.class, AuthControllerTests.ProtectedController.class})
@TestPropertySource(properties = "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository repository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @RestController
    static class ProtectedController {
        @GetMapping("/test-auth/me")
        Long me(@AuthenticationPrincipal Long userId) {
            return userId;
        }
    }

    private void existingUser() {
        User user = new User();
        user.setId(7L);
        user.setNickname("라이더");
        user.setPassword(passwordEncoder.encode("1234"));
        when(repository.findByLoginId("rider")).thenReturn(Optional.of(user));
        when(repository.existsById(7L)).thenReturn(true);
    }

    @Test
    void loginIssuesUsableTokenWithSpecifiedResponse() throws Exception {
        existingUser();
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"rider\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.nickname").value("라이더"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertEquals(7L, tokenProvider.getUserId(token));
        mockMvc.perform(get("/test-auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string("7"));
        // 이전 요청의 인증 정보가 세션으로 유지되지 않는다.
        mockMvc.perform(get("/test-auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserAndWrongPasswordReturnSameFailure() throws Exception {
        existingUser();
        for (String loginId : new String[]{"rider", "unknown"}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\":\"" + loginId + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
        }
    }

    @Test
    void oversizedPasswordDoesNotBecomeServerError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"rider\",\"password\":\"" + "가".repeat(25) + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginRequiresNonblankInputs() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(repository);
    }

    @Test
    void malformedHeadersAndTokensReturn401() throws Exception {
        for (String header : new String[]{"Basic abc", "Bearer ", "Bearer not.a.jwt"}) {
            mockMvc.perform(get("/test-auth/me").header("Authorization", header))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void deletedUserTokenIsRejected() throws Exception {
        mockMvc.perform(get("/test-auth/me").header("Authorization", "Bearer " + tokenProvider.createToken(7L)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredAndWrongSignatureTokensAreRejected() throws Exception {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        String expired = io.jsonwebtoken.Jwts.builder().subject("7")
                .expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(60)))
                .signWith(key).compact();
        String forged = io.jsonwebtoken.Jwts.builder().subject("7")
                .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(60)))
                .signWith(io.jsonwebtoken.Jwts.SIG.HS256.key().build()).compact();
        for (String token : new String[]{expired, forged}) {
            mockMvc.perform(get("/test-auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
        verifyNoInteractions(repository);
    }

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
