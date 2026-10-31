package com.ridesync.server;

import com.ridesync.server.entity.User;
import com.ridesync.server.repository.UserRepository;
import com.ridesync.server.websocket.LoginWebSocketHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.WebSocketSession;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "RIDEMATE_LIVE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "spring.jpa.show-sql=false",
        "JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class LoginWebSocketLiveTests {
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean LoginWebSocketHandler handler;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private CompletableFuture<WebSocket> connect(String token, String query) {
        var builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder.buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/login" + query),
                new WebSocket.Listener() {});
    }

    private void denied(String token, String query) {
        var failure = assertThrows(ExecutionException.class,
                () -> connect(token, query).get(10, TimeUnit.SECONDS));
        var handshake = assertInstanceOf(WebSocketHandshakeException.class, failure.getCause());
        assertEquals(401, handshake.getResponse().statusCode());
    }

    @Test
    void actualLoginTokenAuthenticatesSocketAndInvalidTokensCannotConnect() throws Exception {
        User user = new User();
        String name = "ws_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        user.setLoginId(name);
        user.setNickname(name);
        user.setPassword(encoder.encode(password));
        user.setCreatedAt(Instant.now());
        users.saveAndFlush(user);
        WebSocket socket = null;
        try {
            var json = JsonMapper.builder().build();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("loginId", name, "password", password)))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String token = json.readTree(response.body()).get("accessToken").asText();
            socket = connect(token, "?userId=0").get(10, TimeUnit.SECONDS);
            var captured = ArgumentCaptor.forClass(WebSocketSession.class);
            verify(handler, timeout(3000)).afterConnectionEstablished(captured.capture());
            // spy 호출 관측과 실제 메서드 완료 사이의 경쟁을 피한다.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!captured.getValue().getAttributes().containsKey(LoginWebSocketHandler.USER_ID_ATTRIBUTE)
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(user.getId(), captured.getValue().getAttributes().get(LoginWebSocketHandler.USER_ID_ATTRIBUTE));
            denied(null, "");
            denied(null, "?token=" + token);
            denied("invalid", "");
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + "." +
                    (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
            denied(tampered, "");
            String expired = Jwts.builder().subject(user.getId().toString())
                    .expiration(Date.from(Instant.now().minusSeconds(60)))
                    .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_KEY))).compact();
            denied(expired, "");
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            users.deleteById(user.getId());
            denied(token, "");
            verify(handler, times(1)).afterConnectionEstablished(any());
        } finally {
            if (socket != null) socket.abort();
            if (users.existsById(user.getId())) users.deleteById(user.getId());
        }
    }
}
