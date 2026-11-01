package com.ridesync.server.service;

import com.ridesync.server.dto.CreateRideInvitationRequest;
import com.ridesync.server.entity.*;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.*;
import com.ridesync.server.websocket.OnlineUserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RideInvitationServiceTests {
    @Test
    void responsesAreFinalIdempotentAndBroadcastFullStatus() {
        String id = service.create(1L, request()).invitationId();
        clearInvocations(online);
        service.respond(2L, id, true);
        service.respond(2L, id, true);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(online, times(1)).sendToUser(eq(1L), payload.capture());
        var status = json.readTree(payload.getValue());
        assertEquals(2, status.get("acceptedCount").asInt());
        assertTrue(status.get("canStart").asBoolean());
        assertEquals("ACCEPTED", status.get("participants").get(1).get("status").asText());
        assertEquals("INVITATION_RESPONSE_CONFLICT", assertThrows(ApiException.class,
                () -> service.respond(2L, id, false)).getCode());
        String rejectedId = service.create(1L, request()).invitationId();
        service.respond(2L, rejectedId, false);
        var rejected = service.findActive(rejectedId).orElseThrow().status();
        assertEquals(1, rejected.acceptedCount());
        assertFalse(rejected.canStart());
        assertEquals("REJECTED", rejected.participants().get(1).status());
    }

    @Test
    void forbidsLeaderAndOutsiderAndRejectsMissingOrExpiredInvitation() {
        String id = service.create(1L, request()).invitationId();
        for (Long user : List.of(1L, 99L)) {
            assertEquals("FORBIDDEN", assertThrows(ApiException.class,
                    () -> service.respond(user, id, true)).getCode());
        }
        assertEquals("INVITATION_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.respond(2L, "missing", true)).getCode());
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        assertEquals("INVITATION_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.respond(2L, id, true)).getCode());
    }

    @Test
    void replayTargetsOnlyNewSessionAndSkipsAnsweredUnrelatedAndExpiredInvitations() {
        var session = mock(org.springframework.web.socket.WebSocketSession.class);
        String id = service.create(1L, request()).invitationId();
        clearInvocations(online);
        service.replay(2L, session);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(online).sendToSession(eq(session), payload.capture());
        assertEquals("RIDE_INVITE", json.readTree(payload.getValue()).get("type").asText());
        service.respond(2L, id, true);
        clearInvocations(online);
        service.replay(2L, session);
        service.replay(99L, session);
        verifyNoInteractions(online);
        service.replay(1L, session);
        verify(online).sendToSession(eq(session), payload.capture());
        assertEquals(2, json.readTree(payload.getValue()).get("acceptedCount").asInt());
        clearInvocations(online);
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        service.replay(1L, session);
        service.replay(2L, session);
        verifyNoInteractions(online);
    }

    @Test
    void concurrentOppositeResponsesOnlyCommitOneDecision() throws Exception {
        String id = service.create(1L, request()).invitationId();
        clearInvocations(online);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<java.util.concurrent.Future<String>>();
            for (boolean accepted : new boolean[]{true, false}) {
                results.add(pool.submit(() -> {
                    start.await();
                    try { service.respond(2L, id, accepted); return "OK"; }
                    catch (ApiException e) { return e.getCode(); }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (var future : results) outcomes.add(future.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(outcomes, "OK"));
            assertEquals(1, Collections.frequency(outcomes, "INVITATION_RESPONSE_CONFLICT"));
            verify(online, times(1)).sendToUser(eq(1L), anyString());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentDifferentParticipantsAndReplayNeverRegressStatus() throws Exception {
        var member = new GroupMember();
        var user = new User(); user.setId(3L); user.setNickname("추가"); member.setUser(user);
        var approved = new ArrayList<>(members.findApprovedMembers(10L)); approved.add(member);
        when(members.findApprovedMembers(10L)).thenReturn(approved);
        String id = service.create(1L, new CreateRideInvitationRequest(10L, List.of(2L, 3L))).invitationId();
        List<Integer> delivered = Collections.synchronizedList(new ArrayList<>());
        doAnswer(call -> { delivered.add(json.readTree((String) call.getArgument(1)).get("acceptedCount").asInt()); return null; })
                .when(online).sendToUser(eq(1L), anyString());
        doAnswer(call -> { delivered.add(json.readTree((String) call.getArgument(1)).get("acceptedCount").asInt()); return null; })
                .when(online).sendToSession(any(), anyString());
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            var first = pool.submit(() -> { start.await(); service.respond(2L, id, true); return null; });
            var second = pool.submit(() -> { start.await(); service.respond(3L, id, true); return null; });
            var replay = pool.submit(() -> { start.await(); service.replay(1L, mock(org.springframework.web.socket.WebSocketSession.class)); return null; });
            start.countDown();
            for (var future : List.of(first, second, replay)) future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(3, service.findActive(id).orElseThrow().status().acceptedCount());
            assertEquals(delivered.stream().sorted().toList(), delivered);
            assertEquals(3, delivered.size());
        } finally { pool.shutdownNow(); }
    }

    final RidingGroupRepository groups = mock(RidingGroupRepository.class);
    final GroupMemberRepository members = mock(GroupMemberRepository.class);
    final OnlineUserRegistry online = mock(OnlineUserRegistry.class);
    final Clock clock = mock(Clock.class);
    final JsonMapper json = JsonMapper.builder().build();
    final RideInvitationService service = new RideInvitationService(groups, members, online, json, clock,
            mock(RideSessionRepository.class), mock(RideSessionMemberRepository.class),
            mock(org.springframework.transaction.PlatformTransactionManager.class));
    final Instant now = Instant.parse("2026-11-01T00:00:00Z");
    RidingGroup group;

    @BeforeEach
    void setup() {
        User owner = new User(); owner.setId(1L); owner.setNickname("선두");
        User invitee = new User(); invitee.setId(2L); invitee.setNickname("동료");
        group = new RidingGroup(); group.setId(10L); group.setOwner(owner); group.setGroupName("한강");
        GroupMember member = new GroupMember(); member.setUser(invitee);
        when(groups.findById(10L)).thenReturn(Optional.of(group));
        when(members.existsByGroup_IdAndUser_IdAndStatus(10L, 1L, "APPROVED")).thenReturn(true);
        when(members.findApprovedMembers(10L)).thenReturn(List.of(member));
        when(online.isOnline(anyLong())).thenReturn(true);
        when(clock.instant()).thenReturn(now);
    }

    CreateRideInvitationRequest request() { return new CreateRideInvitationRequest(10L, List.of(2L)); }

    @Test
    void createsUniqueInvitationsWithExactMessagesAndNoDatabaseWrites() {
        var response = service.create(1L, request());
        assertEquals("PENDING", response.status());
        assertEquals(1, response.invitedCount());
        assertEquals(10L, response.groupId());
        var invite = ArgumentCaptor.forClass(String.class);
        var status = ArgumentCaptor.forClass(String.class);
        verify(online).sendToUser(eq(2L), invite.capture());
        verify(online).sendToUser(eq(1L), status.capture());
        var received = json.readTree(invite.getValue());
        assertEquals(6, received.size());
        assertEquals("RIDE_INVITE", received.get("type").asText());
        assertEquals(response.invitationId(), received.get("invitationId").asText());
        assertEquals("한강", received.get("groupName").asText());
        assertEquals(1, received.get("leaderId").asInt());
        var initial = json.readTree(status.getValue());
        assertEquals(1, initial.get("acceptedCount").asInt());
        assertFalse(initial.get("canStart").asBoolean());
        assertEquals("ACCEPTED", initial.get("participants").get(0).get("status").asText());
        assertEquals("PENDING", initial.get("participants").get(1).get("status").asText());
        assertEquals(2, initial.get("participants").size());
        assertTrue(service.findActive(response.invitationId()).isPresent());
        assertNotEquals(response.invitationId(), service.create(1L, request()).invitationId());
        verify(groups, never()).save(any());
        verify(members, never()).save(any());
    }

    @Test
    void rejectsNonOwnerAndUnapprovedOwner() {
        assertEquals("FORBIDDEN", assertThrows(ApiException.class,
                () -> service.create(3L, request())).getCode());
        when(members.existsByGroup_IdAndUser_IdAndStatus(10L, 1L, "APPROVED")).thenReturn(false);
        assertEquals("FORBIDDEN", assertThrows(ApiException.class,
                () -> service.create(1L, request())).getCode());
        verify(online, never()).sendToUser(any(), any());
    }

    @Test
    void rejectsMissingGroupInvalidTargetsAndOfflineUsersWithoutNotifications() {
        assertEquals("GROUP_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.create(1L, new CreateRideInvitationRequest(99L, List.of(2L)))).getCode());
        for (List<Long> ids : List.of(List.of(1L), List.of(2L, 2L))) {
            assertEquals("INVALID_REQUEST", assertThrows(ApiException.class,
                    () -> service.create(1L, new CreateRideInvitationRequest(10L, ids))).getCode());
        }
        assertEquals("INVALID_INVITEE", assertThrows(ApiException.class,
                () -> service.create(1L, new CreateRideInvitationRequest(10L, List.of(2L, 3L)))).getCode());
        for (Long id : List.of(1L, 2L)) {
            when(online.isOnline(id)).thenReturn(false);
            assertEquals("USER_OFFLINE", assertThrows(ApiException.class,
                    () -> service.create(1L, request())).getCode());
            when(online.isOnline(id)).thenReturn(true);
        }
        verify(online, never()).sendToUser(any(), any());
    }

    @Test
    void expiresAtThirtyMinutesAndScheduledCleanupRemovesStoredState() {
        String first = service.create(1L, request()).invitationId();
        String second = service.create(1L, request()).invitationId();
        when(clock.instant()).thenReturn(now.plusSeconds(1799));
        service.removeExpiredInvitations();
        assertTrue(service.findActive(first).isPresent());
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        assertTrue(service.findActive(first).isEmpty());
        service.removeExpiredInvitations();
        // 시간을 되돌려도 정리된 상태가 되살아나지 않는다.
        when(clock.instant()).thenReturn(now);
        assertTrue(service.findActive(first).isEmpty());
        assertTrue(service.findActive(second).isEmpty());
    }
}
