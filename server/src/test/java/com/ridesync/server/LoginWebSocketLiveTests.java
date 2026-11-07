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
    @Autowired com.ridesync.server.security.JwtTokenProvider tokenProvider;
    @Autowired com.ridesync.server.websocket.OnlineUserRegistry onlineUsers;
    @MockitoSpyBean LoginWebSocketHandler handler;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void realHeartbeatKeepsResponsiveSocketAndClosesSilentSocket() throws Exception {
        User user = new User();
        String name = "heartbeat_" + UUID.randomUUID().toString().replace("-", "");
        user.setLoginId(name);
        user.setNickname(name);
        user.setPassword(encoder.encode(UUID.randomUUID().toString()));
        user.setCreatedAt(Instant.now());
        users.saveAndFlush(user);
        WebSocket responsive = null;
        java.net.Socket silent = null;
        var pings = new java.util.concurrent.atomic.AtomicInteger();
        var silentPings = new java.util.concurrent.atomic.AtomicInteger();

        try {
            String token = tokenProvider.createToken(user.getId());
            URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/login");
            responsive = client.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                    .buildAsync(uri, new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onPing(WebSocket ws, java.nio.ByteBuffer data) {
                            pings.incrementAndGet();
                            return WebSocket.Listener.super.onPing(ws, data);
                        }
                    }).get(10, TimeUnit.SECONDS);
            // Java HttpClient는 Pong을 자동 전송하므로 무응답 검증에는 원시 소켓을 사용한다.
            silent = new java.net.Socket();
            silent.connect(new java.net.InetSocketAddress("127.0.0.1", port), 5000);
            silent.setSoTimeout(105000);
            String upgrade = "GET /ws/login HTTP/1.1\r\nHost: 127.0.0.1:" + port
                    + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13"
                    + "\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="
                    + "\r\nAuthorization: Bearer " + token + "\r\n\r\n";
            silent.getOutputStream().write(upgrade.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            var input = new java.io.DataInputStream(silent.getInputStream());
            StringBuilder headers = new StringBuilder();
            while (!headers.toString().endsWith("\r\n\r\n")) {
                headers.append((char) input.readUnsignedByte());
                assertTrue(headers.length() < 16384);
            }
            assertTrue(headers.toString().startsWith("HTTP/1.1 101"));
            long heartbeatDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(105);
            int closeCode = -1;
            while (System.nanoTime() < heartbeatDeadline) {
                int opcode = input.readUnsignedByte() & 15;
                int length = input.readUnsignedByte();
                assertTrue(length < 126, "서버 제어 프레임은 마스킹 없는 125바이트 이하여야 합니다.");
                byte[] payload = input.readNBytes(length);
                assertEquals(length, payload.length);
                if (opcode == 9) silentPings.incrementAndGet(); // Pong을 보내지 않는다.
                if (opcode == 8) {
                    assertTrue(payload.length >= 2);
                    closeCode = ((payload[0] & 255) << 8) | (payload[1] & 255);
                    break;
                }
            }
            assertEquals(1008, closeCode);
            assertTrue(pings.get() >= 2);
            assertTrue(silentPings.get() >= 2);
            assertFalse(responsive.isInputClosed());
            assertTrue(onlineUsers.isOnline(user.getId()));
            responsive.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            awaitOnline(user.getId(), false);
        } finally {
            if (responsive != null) responsive.abort();
            if (silent != null) silent.close();
            users.deleteById(user.getId());
        }
    }

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
        WebSocket secondSocket = null;
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
            awaitOnline(user.getId(), true);
            secondSocket = connect(token, "").get(10, TimeUnit.SECONDS);
            verify(handler, timeout(3000).times(2)).afterConnectionEstablished(any());
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
            verify(handler, timeout(3000)).afterConnectionClosed(any(), any());
            assertTrue(onlineUsers.isOnline(user.getId()));
            secondSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            awaitOnline(user.getId(), false);
            String shortLived = Jwts.builder().subject(user.getId().toString())
                    .expiration(Date.from(Instant.now().plusSeconds(3)))
                    .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_KEY))).compact();
            secondSocket = connect(shortLived, "").get(10, TimeUnit.SECONDS);
            awaitOnline(user.getId(), true);
            awaitOnline(user.getId(), false);
            long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!secondSocket.isInputClosed() && System.nanoTime() < closeDeadline) Thread.sleep(10);
            assertTrue(secondSocket.isInputClosed());
            users.deleteById(user.getId());
            denied(token, "");
            verify(handler, times(3)).afterConnectionEstablished(any());
        } finally {
            if (socket != null) socket.abort();
            if (secondSocket != null) secondSocket.abort();
            if (users.existsById(user.getId())) users.deleteById(user.getId());
        }
    }

    private void awaitOnline(Long id, boolean expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (onlineUsers.isOnline(id) != expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(expected, onlineUsers.isOnline(id));
    }
}
