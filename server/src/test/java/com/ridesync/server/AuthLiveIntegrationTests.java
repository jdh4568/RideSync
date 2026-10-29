package com.ridesync.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 설정된 MySQL과 임시 포트의 서버를 사용한다. 생성한 검증 계정은 종료 시 삭제한다. */
@EnabledIfEnvironmentVariable(named = "RIDEMATE_LIVE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.show-sql=false", "server.address=127.0.0.1"})
class AuthLiveIntegrationTests {
    @Value("${local.server.port}")
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void verifyRealDatabaseAndHttpAuthenticationFlow() throws Exception {
        List<String> uniqueColumns = jdbc.queryForList("""
                SELECT GROUP_CONCAT(LOWER(COLUMN_NAME) ORDER BY SEQ_IN_INDEX) AS columns_in_index
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND NON_UNIQUE = 0
                GROUP BY INDEX_NAME
                """, String.class);
        assertTrue(uniqueColumns.contains("login_id"), "실제 DB에 login_id 단일 UNIQUE 제약이 필요합니다.");
        assertTrue(uniqueColumns.contains("nickname"), "실제 DB에 nickname 단일 UNIQUE 제약이 필요합니다.");
        System.out.println("LIVE PASS: MySQL connection and login_id/nickname UNIQUE indexes");

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String loginId = "stage4_" + suffix;
        String nickname = "검증_" + suffix;
        String concurrentId = "race_" + suffix;
        String password = UUID.randomUUID().toString();
        try {
            var available = call("GET", "/api/v1/auth/check-login-id?loginId=" + loginId, null, null);
            assertEquals(200, available.statusCode());
            assertTrue(body(available).get("available").asBoolean());

            var signup = call("POST", "/api/v1/auth/signup", signup(loginId, nickname, password), null);
            assertEquals(201, signup.statusCode(), "회원가입 HTTP 상태");
            long userId = body(signup).get("userId").asLong();
            assertEquals(nickname, body(signup).get("nickname").asText());
            assertEquals(2, body(signup).size());
            var row = jdbc.queryForMap("SELECT password, created_at FROM user WHERE user_id = ?", userId);
            String storedHash = (String) row.get("password");
            assertTrue(storedHash.startsWith("$2"));
            assertTrue(passwordEncoder.matches(password, storedHash));
            assertNotNull(row.get("created_at"));
            System.out.println("LIVE PASS: signup 201, BCrypt storage, created_at and response fields");

            assertFalse(body(call("GET", "/api/v1/auth/check-login-id?loginId=" + loginId, null, null))
                    .get("available").asBoolean());
            String encodedNickname = java.net.URLEncoder.encode(nickname, java.nio.charset.StandardCharsets.UTF_8);
            var nicknameCheck = call("GET", "/api/v1/auth/check-nickname?nickname=" + encodedNickname, null, null);
            assertEquals(200, nicknameCheck.statusCode());
            assertFalse(body(nicknameCheck).get("available").asBoolean());
            assertError(call("POST", "/api/v1/auth/signup", signup(loginId, nickname, password), null),
                    409, "DUPLICATE_LOGIN_ID");
            assertError(call("POST", "/api/v1/auth/signup", signup(concurrentId, nickname, password), null),
                    409, "DUPLICATE_NICKNAME");
            assertError(call("POST", "/api/v1/auth/signup", Map.of("loginId", concurrentId,
                    "nickname", "race_" + suffix, "password", password, "passwordConfirm", "wrong"), null),
                    400, "PASSWORD_MISMATCH");
            assertError(call("POST", "/api/v1/auth/signup", Map.of(), null), 400, "INVALID_REQUEST");
            System.out.println("LIVE PASS: availability, duplicate 409 and invalid signup 400");

            var login = call("POST", "/api/v1/auth/login", Map.of("loginId", loginId, "password", password), null);
            assertEquals(200, login.statusCode());
            assertEquals(userId, body(login).get("userId").asLong());
            assertEquals(nickname, body(login).get("nickname").asText());
            assertEquals(3, body(login).size());
            String token = body(login).get("accessToken").asText();
            assertError(call("POST", "/api/v1/auth/login", Map.of("loginId", loginId, "password", "wrong"), null),
                    401, "INVALID_CREDENTIALS");
            assertError(call("POST", "/api/v1/auth/login", Map.of("loginId", concurrentId, "password", password), null),
                    401, "INVALID_CREDENTIALS");
            assertError(call("GET", "/test", null, null), 401, "UNAUTHORIZED");
            var authenticated = call("GET", "/test", null, token);
            assertEquals(200, authenticated.statusCode());
            assertEquals("test", authenticated.body());
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
            assertError(call("GET", "/test", null, tampered), 401, "INVALID_TOKEN");
            assertError(call("GET", "/test", null, "invalid"), 401, "INVALID_TOKEN");
            assertError(call("GET", "/test", null, null), 401, "UNAUTHORIZED");
            System.out.println("LIVE PASS: login 200, credentials 401, protected route 200, missing/tampered token 401");

            var request = request("POST", "/api/v1/auth/signup", signup(concurrentId, "race_" + suffix, password), null);
            var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var statuses = List.of(first.join().statusCode(), second.join().statusCode()).stream().sorted().toList();
            assertEquals(List.of(201, 409), statuses);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE login_id = ?", Integer.class, concurrentId));
            System.out.println("LIVE PASS: two concurrent signups -> 201/409, exactly one row");
        } finally {
            jdbc.update("DELETE FROM user WHERE login_id IN (?, ?)", loginId, concurrentId);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE login_id IN (?, ?)",
                    Integer.class, loginId, concurrentId));
            System.out.println("LIVE PASS: generated test accounts cleaned up");
        }
    }

    private Map<String, String> signup(String loginId, String nickname, String password) {
        return Map.of("loginId", loginId, "nickname", nickname, "password", password, "passwordConfirm", password);
    }

    private HttpRequest request(String method, String path, Object payload, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (payload == null) return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
    }

    private HttpResponse<String> call(String method, String path, Object payload, String token) throws Exception {
        return client.send(request(method, path, payload, token), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(code, body(response).get("code").asText());
    }
}
