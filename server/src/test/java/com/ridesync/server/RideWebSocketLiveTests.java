package com.ridesync.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 MySQL·HTTP·WebSocket 기반 라이딩 수신호 검증. */
@EnabledIfEnvironmentVariable(named = "RIDEMATE_LIVE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.jpa.show-sql=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RideWebSocketLiveTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final List<String> accounts = new ArrayList<>();
    final List<Long> groups = new ArrayList<>();
    final List<Peer> peers = new ArrayList<>();

    record Account(long id, String token, String nickname) {}

    JsonNode call(String path, Account account, Object body, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (account != null) request.header("Authorization", "Bearer " + account.token());
        var response = client.send(request.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), path + ": " + response.body());
        return json.readTree(response.body());
    }

    Account account() throws Exception {
        String name = "invite_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        accounts.add(name);
        var signup = call("/api/v1/auth/signup", null, Map.of("loginId", name, "nickname", name,
                "password", password, "passwordConfirm", password), 201);
        var login = call("/api/v1/auth/login", null, Map.of("loginId", name, "password", password), 200);
        return new Account(signup.get("userId").asLong(), login.get("accessToken").asText(), name);
    }

    long group(Account owner, Account... members) throws Exception {
        long id = call("/api/v1/groups", owner, Map.of("groupName", "초대 통합 검증", "maxMembers", 10), 201)
                .get("groupId").asLong();
        groups.add(id);
        for (var member : members) {
            call("/api/v1/groups/" + id + "/join", member, null, 200);
            call("/api/v1/groups/" + id + "/requests/" + member.id() + "/approve", owner, null, 200);
        }
        return id;
    }

    void awaitOnline(long group, Account owner, Account member, boolean expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/groups/" + group + "/members"))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + owner.token()).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            for (var row : json.readTree(response.body())) {
                if (row.get("userId").asLong() == member.id() && row.get("online").asBoolean() == expected) return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("온라인 상태가 변경되지 않음: " + member.id());
    }

    class Peer implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder fragments = new StringBuilder();
        final CompletableFuture<Void> closed = new CompletableFuture<>();
        volatile Throwable error;
        WebSocket socket;

        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            ws.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            closed.complete(null); return null;
        }
        @Override public void onError(WebSocket ws, Throwable failure) { error = failure; closed.completeExceptionally(failure); }
        void send(Object message) throws Exception { raw(json.writeValueAsString(message)); }
        void raw(String message) throws Exception { socket.sendText(message, true).get(5, TimeUnit.SECONDS); }
        JsonNode next(String type) throws Exception {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertNull(error, "웹소켓 통신 오류");
            assertNotNull(message, "수신 대기 시간 초과: " + type);
            var result = json.readTree(message);
            assertEquals(type, result.path("type").asText(), message);
            return result;
        }
        void quiet() throws Exception {
            assertNull(messages.poll(250, TimeUnit.MILLISECONDS), "예상하지 않은 추가 메시지");
            assertNull(error);
        }
        void close() throws Exception {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "test").get(5, TimeUnit.SECONDS);
            closed.get(5, TimeUnit.SECONDS);
        }
    }

    Peer connect(Account account) throws Exception {
        Peer peer = new Peer(); peers.add(peer);
        peer.socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + account.token())
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/login"), peer).get(10, TimeUnit.SECONDS);
        return peer;
    }

    Map<String, Object> response(String id, boolean accepted) {
        return Map.of("type", "RIDE_INVITE_RESPONSE", "invitationId", id, "accepted", accepted);
    }

    void error(Peer peer, String code) throws Exception {
        var message = peer.next("ERROR");
        assertEquals(3, message.size());
        assertEquals(code, message.get("code").asText());
    }

    record Riding(Account owner, Account member, long id) {}
    Riding riding() throws Exception {
        var owner = account(); var member = account(); long group = group(owner, member);
        var leader = connect(owner); var rider = connect(member);
        awaitOnline(group, owner, owner, true); awaitOnline(group, owner, member, true);
        String id = call("/api/v1/ride-invitations", owner, Map.of("groupId", group, "inviteeIds", List.of(member.id())), 201).get("invitationId").asText();
        rider.next("RIDE_INVITE"); leader.next("RIDE_INVITE_STATUS");
        rider.send(response(id, true)); leader.next("RIDE_INVITE_STATUS");
        long ride = call("/api/v1/ride-invitations/" + id + "/start", owner, null, 200).get("rideSessionId").asLong();
        leader.next("RIDE_STARTED"); rider.next("RIDE_STARTED");
        return new Riding(owner, member, ride);
    }
    Peer ridePeer(Account account, long ride) throws Exception {
        Peer peer = new Peer(); peers.add(peer);
        peer.socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + account.token())
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/ride/" + ride), peer).get(10, TimeUnit.SECONDS);
        return peer;
    }
    @Test void routesSignalsToAllMembersOnlyAndSupportsReconnect() throws Exception {
        var a = riding(); var b = riding();
        var leader = ridePeer(a.owner(), a.id()); var member = ridePeer(a.member(), a.id());
        var second = ridePeer(a.member(), a.id()); var foreign = ridePeer(b.member(), b.id());
        for (var p : List.of(leader, member, second, foreign)) p.quiet();
        for (String signal : List.of("LEFT","RIGHT","STOP","SLOW","ROAD_HAZARD","SINGLE_FILE","DOUBLE_FILE","LEADER_CHANGE")) {
            leader.send(Map.of("type","RIDE_SIGNAL","signal",signal,"senderId",999,"rideSessionId",b.id()));
            for (var p : List.of(member, second)) {
                var message = p.next("RIDE_SIGNAL"); assertEquals(3,message.size());
                assertEquals(a.owner().id(),message.get("senderId").asLong()); assertEquals(signal,message.get("signal").asText());
            }
        }
        leader.quiet(); foreign.quiet();
        member.send(Map.of("type","RIDE_SIGNAL","signal","LEFT")); error(member,"FORBIDDEN"); second.quiet();
        for(String raw : List.of("{", "null", "{}", "{\"type\":\"RIDE_SIGNAL\",\"signal\":\"BAD\"}", "{\"type\":\"RIDE_SIGNAL\",\"signal\":1}", "{\"type\":\"RIDE_SIGNAL\",\"signal\":\"LEFT\"} {}")) {
            leader.raw(raw); error(leader,"INVALID_MESSAGE");
        }
        leader.send(Map.of("type","RIDE_FINISHED")); error(leader,"UNSUPPORTED_MESSAGE_TYPE");
        member.close(); var restored = ridePeer(a.member(),a.id()); restored.quiet();
        leader.send(Map.of("type","RIDE_SIGNAL","signal","STOP")); restored.next("RIDE_SIGNAL"); second.next("RIDE_SIGNAL");
        assertEquals("RIDING", jdbc.queryForObject("select status from ride_session where ride_session_id=?",String.class,a.id()));
        assertEquals("LEADER", jdbc.queryForObject("select role from ride_session_member where ride_session_id=? and user_id=?",String.class,a.id(),a.owner().id()));
    }
    @Test void deniesUnauthenticatedNonParticipantsMissingAndInactiveRides() throws Exception {
        var r = riding(); var outsider = account();
        var unauthorized = client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/ride/" + r.id()), new Peer());
        var failure = assertThrows(ExecutionException.class, () -> unauthorized.get(10,TimeUnit.SECONDS));
        assertInstanceOf(WebSocketHandshakeException.class,failure.getCause());
        assertEquals(401,((WebSocketHandshakeException)failure.getCause()).getResponse().statusCode());
        var denied = ridePeer(outsider,r.id()); error(denied,"FORBIDDEN"); denied.closed.get(5,TimeUnit.SECONDS);
        var missing = ridePeer(r.owner(),Long.MAX_VALUE); error(missing,"RIDE_SESSION_NOT_FOUND"); missing.closed.get(5,TimeUnit.SECONDS);
        var leader = ridePeer(r.owner(),r.id()); leader.quiet();
        // 테스트가 만든 세션만 변경한다. 종료 API 구현은 다음 단계이다.
        jdbc.update("update ride_session set status='FINISHED' where ride_session_id=?",r.id());
        var ended = ridePeer(r.member(),r.id()); error(ended,"RIDE_NOT_ACTIVE"); ended.closed.get(5,TimeUnit.SECONDS);
        leader.send(Map.of("type","RIDE_SIGNAL","signal","LEFT")); error(leader,"RIDE_NOT_ACTIVE"); leader.closed.get(5,TimeUnit.SECONDS);
    }
    void assertNoRideRecords(long group) {
        assertEquals(0, jdbc.queryForObject("select count(*) from ride_session where group_id=?", Integer.class, group));
        assertEquals(0, jdbc.queryForObject("select count(*) from ride_session_member m join ride_session s on m.ride_session_id=s.ride_session_id where s.group_id=?", Integer.class, group));
    }

    @AfterEach
    void cleanup() {
        for (var peer : peers) if (peer.socket != null) peer.socket.abort();
        for (long group : groups) {
            jdbc.update("delete m from ride_session_member m join ride_session s on m.ride_session_id=s.ride_session_id where s.group_id=?", group);
            jdbc.update("delete from ride_session where group_id=?", group);
            assertNoRideRecords(group);
            jdbc.update("delete from group_member where group_id=?", group);
            jdbc.update("delete from riding_group where group_id=?", group);
            assertEquals(0, jdbc.queryForObject("select count(*) from riding_group where group_id=?", Integer.class, group));
        }
        for (String name : accounts) {
            jdbc.update("delete from user where login_id=?", name);
            assertEquals(0, jdbc.queryForObject("select count(*) from user where login_id=?", Integer.class, name));
        }
    }
}
