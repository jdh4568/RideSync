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

/** 실제 MySQL, HTTP 및 표준 WebSocket. 롤백 테스트만 실제 저장 직후 예외를 주입한다. */
@EnabledIfEnvironmentVariable(named = "RIDEMATE_LIVE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.jpa.show-sql=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RideInvitationLiveIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.ridesync.server.repository.RideSessionMemberRepository rideMembers;
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

    void status(Peer peer, String id, int accepted, Map<Long, String> states) throws Exception {
        var message = peer.next("RIDE_INVITE_STATUS");
        assertEquals(id, message.get("invitationId").asText());
        assertEquals(accepted, message.get("acceptedCount").asInt());
        assertEquals(accepted >= 2, message.get("canStart").asBoolean());
        Map<Long, String> actual = new HashMap<>();
        for (var participant : message.get("participants")) {
            assertFalse(participant.get("nickname").asText().isBlank());
            actual.put(participant.get("userId").asLong(), participant.get("status").asText());
        }
        assertEquals(states, actual);
        assertEquals(states.size(), message.get("participants").size());
    }

    void invite(Peer peer, String id, long group, Account owner) throws Exception {
        var message = peer.next("RIDE_INVITE");
        assertEquals(6, message.size());
        assertEquals(id, message.get("invitationId").asText());
        assertEquals(group, message.get("groupId").asLong());
        assertEquals(owner.id(), message.get("leaderId").asLong());
        assertEquals(owner.nickname(), message.get("leaderNickname").asText());
    }

    @Test
    void fullInvitationLifecycleWithMultipleConnectionsErrorsAndReconnect() throws Exception {
        var owner = account(); var member = account(); var reject = account(); var outsider = account();
        long group = group(owner, member, reject);
        var leader = connect(owner); var leaderSecond = connect(owner);
        var rider = connect(member); var riderSecond = connect(member);
        var declining = connect(reject); var foreign = connect(outsider);
        for (var user : List.of(owner, member, reject)) awaitOnline(group, owner, user, true);
        String id = call("/api/v1/ride-invitations", owner,
                Map.of("groupId", group, "inviteeIds", List.of(member.id(), reject.id())), 201).get("invitationId").asText();
        invite(rider, id, group, owner); invite(riderSecond, id, group, owner); invite(declining, id, group, owner);
        var states = new HashMap<>(Map.of(owner.id(), "ACCEPTED", member.id(), "PENDING", reject.id(), "PENDING"));
        status(leader, id, 1, states); status(leaderSecond, id, 1, states); foreign.quiet();

        rider.close(); riderSecond.close(); awaitOnline(group, owner, member, false);
        rider = connect(member); invite(rider, id, group, owner);
        leader.quiet(); leaderSecond.quiet();
        foreign.send(Map.of("type", "RIDE_INVITE_RESPONSE", "invitationId", id, "accepted", true, "userId", member.id()));
        error(foreign, "FORBIDDEN");
        leader.send(response(id, true)); error(leader, "FORBIDDEN"); leaderSecond.quiet();
        rider.raw("{broken"); error(rider, "INVALID_MESSAGE");
        rider.send(Map.of("type", "RIDE_INVITE_RESPONSE", "invitationId", id, "accepted", "false"));
        error(rider, "INVALID_MESSAGE");
        rider.send(response(id, true)); states.put(member.id(), "ACCEPTED");
        status(leader, id, 2, states); status(leaderSecond, id, 2, states);
        rider.send(response(id, true)); rider.send(Map.of("type", "UNKNOWN"));
        error(rider, "UNSUPPORTED_MESSAGE_TYPE"); leader.quiet(); leaderSecond.quiet();
        rider.send(response(id, false)); error(rider, "INVITATION_RESPONSE_CONFLICT");
        declining.send(response(id, false)); states.put(reject.id(), "REJECTED");
        status(leader, id, 2, states); status(leaderSecond, id, 2, states);
        rider.close(); declining.close(); leader.close(); leaderSecond.close();
        awaitOnline(group, owner, owner, false);
        var restoredLeader = connect(owner); status(restoredLeader, id, 2, states);
        var restoredRider = connect(member); var restoredReject = connect(reject);
        restoredRider.quiet(); restoredReject.quiet(); restoredLeader.quiet();
        restoredRider.send(response("inv-missing", true)); error(restoredRider, "INVITATION_NOT_FOUND");
        assertNoRideRecords(group);
        assertEquals(3, jdbc.queryForObject("select count(*) from group_member where group_id=? and status='APPROVED'", Integer.class, group));
    }

    @Test
    void creationRejectsInvalidPermissionsTargetsAndOfflineUsersWithoutMessages() throws Exception {
        var owner = account(); var member = account(); var pending = account();
        long group = group(owner, member);
        call("/api/v1/groups/" + group + "/join", pending, null, 200);
        var leader = connect(owner); awaitOnline(group, owner, owner, true);
        var body = Map.of("groupId", group, "inviteeIds", List.of(member.id()));
        call("/api/v1/ride-invitations", null, body, 401);
        assertEquals("FORBIDDEN", call("/api/v1/ride-invitations", member,
                Map.of("groupId", group, "inviteeIds", List.of(owner.id())), 403).get("code").asText());
        for (var ids : List.of(List.of(owner.id()), List.of(member.id(), member.id()), List.<Long>of())) {
            assertEquals("INVALID_REQUEST", call("/api/v1/ride-invitations", owner,
                    Map.of("groupId", group, "inviteeIds", ids), 400).get("code").asText());
        }
        assertEquals("INVALID_INVITEE", call("/api/v1/ride-invitations", owner,
                Map.of("groupId", group, "inviteeIds", List.of(member.id(), pending.id())), 400).get("code").asText());
        assertEquals("USER_OFFLINE", call("/api/v1/ride-invitations", owner, body, 409).get("code").asText());
        leader.quiet();
        var rider = connect(member); awaitOnline(group, owner, member, true); rider.quiet();
        leader.close(); awaitOnline(group, owner, owner, false);
        assertEquals("USER_OFFLINE", call("/api/v1/ride-invitations", owner, body, 409).get("code").asText());
        rider.quiet();
        assertNoRideRecords(group);
    }

    @Test
    void concurrentRepliesFromTwoDevicesCommitOnceAndBroadcastInOrder() throws Exception {
        var owner = account(); var member = account();
        long group = group(owner, member);
        var leader = connect(owner); var first = connect(member); var second = connect(member);
        awaitOnline(group, owner, owner, true); awaitOnline(group, owner, member, true);
        String id = call("/api/v1/ride-invitations", owner,
                Map.of("groupId", group, "inviteeIds", List.of(member.id())), 201).get("invitationId").asText();
        invite(first, id, group, owner); invite(second, id, group, owner);
        status(leader, id, 1, Map.of(owner.id(), "ACCEPTED", member.id(), "PENDING"));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var accept = pool.submit(() -> { start.await(); first.send(response(id, true)); return null; });
            var reject = pool.submit(() -> { start.await(); second.send(response(id, false)); return null; });
            start.countDown(); accept.get(5, TimeUnit.SECONDS); reject.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        var update = leader.next("RIDE_INVITE_STATUS");
        String decision = update.get("participants").get(1).get("status").asText();
        assertTrue(Set.of("ACCEPTED", "REJECTED").contains(decision));
        boolean accepted = decision.equals("ACCEPTED");
        assertEquals(accepted ? 2 : 1, update.get("acceptedCount").asInt());
        assertEquals(accepted, update.get("canStart").asBoolean());
        error(accepted ? second : first, "INVITATION_RESPONSE_CONFLICT");
        (accepted ? first : second).quiet(); leader.quiet();
        var replay = connect(owner);
        status(replay, id, accepted ? 2 : 1, Map.of(owner.id(), "ACCEPTED", member.id(), decision));
        leader.quiet();
        assertNoRideRecords(group);
    }

    JsonNode getRide(long ride, Account account, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/rides/" + ride));
        if (account != null) request.header("Authorization", "Bearer " + account.token());
        var result = client.send(request.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, result.statusCode(), result.body());
        return json.readTree(result.body());
    }

    record Ready(Account owner, Account member, long group, String id, Peer leader, Peer rider) {}

    Ready ready() throws Exception {
        var owner = account(); var member = account(); long group = group(owner, member);
        var leader = connect(owner); var rider = connect(member);
        awaitOnline(group, owner, owner, true); awaitOnline(group, owner, member, true);
        String id = call("/api/v1/ride-invitations", owner,
                Map.of("groupId", group, "inviteeIds", List.of(member.id())), 201).get("invitationId").asText();
        invite(rider, id, group, owner); leader.next("RIDE_INVITE_STATUS");
        rider.send(response(id, true)); assertEquals(2, leader.next("RIDE_INVITE_STATUS").get("acceptedCount").asInt());
        return new Ready(owner, member, group, id, leader, rider);
    }

    void started(Peer peer, long ride, long group) throws Exception {
        assertEquals(json.readTree("{\"type\":\"RIDE_STARTED\",\"rideSessionId\":" + ride + ",\"groupId\":" + group + "}"), peer.next("RIDE_STARTED"));
    }

    void assertSaved(long ride, long group, Account owner, Account member) {
        assertEquals(1, jdbc.queryForObject("select count(*) from ride_session where group_id=?", Integer.class, group));
        assertEquals(1, jdbc.queryForObject("select count(*) from ride_session where ride_session_id=? and group_id=? and leader_id=? and status='RIDING' and started_at is not null and created_at is not null and ended_at is null", Integer.class, ride, group, owner.id()));
        assertEquals(2, jdbc.queryForObject("select count(*) from ride_session_member where ride_session_id=?", Integer.class, ride));
        for (var user : List.of(owner, member)) {
            assertEquals(1, jdbc.queryForObject("select count(*) from ride_session_member where ride_session_id=? and user_id=? and role=? and joined_at is not null and distance is null and average_speed is null", Integer.class, ride, user.id(), user == owner ? "LEADER" : "MEMBER"));
        }
    }

    @Test
    void startNotifiesAllConfirmedConnectionsAndQueryEnforcesSessionMembership() throws Exception {
        var owner = account(); var member = account(); var rejected = account(); var pending = account(); var outsider = account();
        long group = group(owner, member, rejected, pending);
        var leader = connect(owner); var secondLeader = connect(owner);
        var rider = connect(member); var secondRider = connect(member);
        var declining = connect(rejected); var waiting = connect(pending);
        for (var user : List.of(owner, member, rejected, pending)) awaitOnline(group, owner, user, true);
        String id = call("/api/v1/ride-invitations", owner, Map.of("groupId", group, "inviteeIds", List.of(member.id(), rejected.id(), pending.id())), 201).get("invitationId").asText();
        for (var peer : List.of(rider, secondRider, declining, waiting)) invite(peer, id, group, owner);
        leader.next("RIDE_INVITE_STATUS"); secondLeader.next("RIDE_INVITE_STATUS");
        String path = "/api/v1/ride-invitations/" + id + "/start";
        call(path, null, null, 401); call(path, member, null, 403);
        assertEquals("INSUFFICIENT_PARTICIPANTS", call(path, owner, null, 409).get("code").asText());
        assertNoRideRecords(group);
        rider.send(response(id, true)); leader.next("RIDE_INVITE_STATUS"); secondLeader.next("RIDE_INVITE_STATUS");
        declining.send(response(id, false)); leader.next("RIDE_INVITE_STATUS"); secondLeader.next("RIDE_INVITE_STATUS");
        var result = call(path, owner, null, 200);
        assertEquals(4, result.size()); assertEquals("RIDING", result.get("status").asText());
        assertEquals(group, result.get("groupId").asLong());
        java.time.LocalDateTime.parse(result.get("startedAt").asText());
        long ride = result.get("rideSessionId").asLong(); assertSaved(ride, group, owner, member);
        for (var peer : List.of(leader, secondLeader, rider, secondRider)) { started(peer, ride, group); peer.quiet(); }
        declining.quiet(); waiting.quiet();
        for (var user : List.of(owner, member)) {
            var view = getRide(ride, user, 200); assertEquals(2, view.size());
            assertEquals("초대 통합 검증", view.get("groupName").asText()); assertEquals(2, view.get("participantCount").asInt());
        }
        for (var user : List.of(rejected, pending, outsider)) assertEquals("FORBIDDEN", getRide(ride, user, 403).get("code").asText());
        getRide(ride, null, 401); assertEquals("RIDE_SESSION_NOT_FOUND", getRide(Long.MAX_VALUE, owner, 404).get("code").asText());
        assertEquals("INVITATION_NOT_FOUND", call(path, owner, null, 404).get("code").asText());
        waiting.send(response(id, true)); error(waiting, "INVITATION_NOT_FOUND");
        connect(owner).quiet(); connect(pending).quiet();
    }

    @Test
    void simultaneousHttpStartsPersistExactlyOneSession() throws Exception {
        var r = ready(); var gate = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        Callable<HttpResponse<String>> request = () -> {
            gate.await();
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/ride-invitations/" + r.id() + "/start"))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + r.owner().token())
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        };
        try {
            var a = pool.submit(request); var b = pool.submit(request); gate.countDown();
            var results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(List.of(200, 404), results.stream().map(HttpResponse::statusCode).sorted().toList());
            long ride = json.readTree(results.stream().filter(x -> x.statusCode() == 200).findFirst().orElseThrow().body()).get("rideSessionId").asLong();
            assertSaved(ride, r.group(), r.owner(), r.member());
            started(r.leader(), ride, r.group()); started(r.rider(), ride, r.group()); r.leader().quiet(); r.rider().quiet();
        } finally { pool.shutdownNow(); }
    }

    @Test
    void actualMysqlWritesRollbackWithoutNotificationAndSameInvitationCanRetry() throws Exception {
        var r = ready(); var written = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            // Spring Data 프록시의 추상 save를 직접 호출하지 않고 실제 saveAndFlush로 위임한다.
            rideMembers.saveAndFlush(invocation.getArgument(0));
            assertEquals(1, jdbc.queryForObject("select count(*) from ride_session where group_id=?", Integer.class, r.group()));
            assertEquals(1, jdbc.queryForObject("select count(*) from ride_session_member m join ride_session s on m.ride_session_id=s.ride_session_id where s.group_id=?", Integer.class, r.group()));
            written.set(true);
            throw new IllegalStateException("Intentional test failure after real MySQL insert");
        }).when(rideMembers).save(org.mockito.ArgumentMatchers.any(com.ridesync.server.entity.RideSessionMember.class));
        String path = "/api/v1/ride-invitations/" + r.id() + "/start";
        try {
            assertEquals("INTERNAL_SERVER_ERROR", call(path, r.owner(), null, 500).get("code").asText());
        } finally { org.mockito.Mockito.reset(rideMembers); }
        assertTrue(written.get()); assertNoRideRecords(r.group()); r.leader().quiet(); r.rider().quiet();
        var restored = connect(r.owner()); assertEquals(2, restored.next("RIDE_INVITE_STATUS").get("acceptedCount").asInt());
        long ride = call(path, r.owner(), null, 200).get("rideSessionId").asLong();
        assertSaved(ride, r.group(), r.owner(), r.member());
        started(r.leader(), ride, r.group()); started(restored, ride, r.group()); started(r.rider(), ride, r.group());
    }

    @Test
    void acceptedOfflineMemberRemainsParticipant() throws Exception {
        var r = ready(); r.rider().close(); awaitOnline(r.group(), r.owner(), r.member(), false);
        long ride = call("/api/v1/ride-invitations/" + r.id() + "/start", r.owner(), null, 200).get("rideSessionId").asLong();
        assertSaved(ride, r.group(), r.owner(), r.member()); started(r.leader(), ride, r.group());
        assertEquals(2, getRide(ride, r.member(), 200).get("participantCount").asInt());
        connect(r.member()).quiet();
    }
    @Test
    void pendingResponseRacingStartMatchesPersistedParticipants() throws Exception {
        var r = ready(); var late = account();
        call("/api/v1/groups/" + r.group() + "/join", late, null, 200);
        call("/api/v1/groups/" + r.group() + "/requests/" + late.id() + "/approve", r.owner(), null, 200);
        var peer = connect(late); awaitOnline(r.group(), r.owner(), late, true);
        String id = call("/api/v1/ride-invitations", r.owner(), Map.of("groupId", r.group(), "inviteeIds", List.of(r.member().id(), late.id())), 201).get("invitationId").asText();
        invite(r.rider(), id, r.group(), r.owner()); invite(peer, id, r.group(), r.owner()); r.leader().next("RIDE_INVITE_STATUS");
        r.rider().send(response(id, true)); r.leader().next("RIDE_INVITE_STATUS");
        var gate = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var start = pool.submit(() -> { gate.await(); return call("/api/v1/ride-invitations/" + id + "/start", r.owner(), null, 200); });
            var reply = pool.submit(() -> { gate.await(); peer.send(response(id, true)); return true; });
            gate.countDown(); long ride = start.get(15, TimeUnit.SECONDS).get("rideSessionId").asLong(); reply.get(10, TimeUnit.SECONDS);
            int included = jdbc.queryForObject("select count(*) from ride_session_member where ride_session_id=? and user_id=?", Integer.class, ride, late.id());
            assertTrue(included == 0 || included == 1);
            assertEquals(1, jdbc.queryForObject("select count(*) from ride_session where group_id=?", Integer.class, r.group()));
            assertEquals(2 + included, jdbc.queryForObject("select count(*) from ride_session_member where ride_session_id=?", Integer.class, ride));
            if (included == 1) {
                var status = r.leader().next("RIDE_INVITE_STATUS"); assertEquals(3, status.get("acceptedCount").asInt());
                started(peer, ride, r.group()); getRide(ride, late, 200);
            } else { error(peer, "INVITATION_NOT_FOUND"); getRide(ride, late, 403); }
            started(r.leader(), ride, r.group()); started(r.rider(), ride, r.group());
            r.leader().quiet(); r.rider().quiet(); peer.quiet();
        } finally { pool.shutdownNow(); }
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
