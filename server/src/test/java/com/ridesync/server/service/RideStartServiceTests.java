package com.ridesync.server.service;

import com.ridesync.server.dto.CreateRideInvitationRequest;
import com.ridesync.server.entity.*;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.*;
import com.ridesync.server.websocket.OnlineUserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RideStartServiceTests {
    final RidingGroupRepository groups = mock(RidingGroupRepository.class);
    final GroupMemberRepository members = mock(GroupMemberRepository.class);
    final RideSessionRepository rides = mock(RideSessionRepository.class);
    final RideSessionMemberRepository rideMembers = mock(RideSessionMemberRepository.class);
    final OnlineUserRegistry online = mock(OnlineUserRegistry.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final Clock clock = mock(Clock.class);
    final JsonMapper json = JsonMapper.builder().build();
    final RideInvitationService service = new RideInvitationService(groups, members, online, json, clock,
            rides, rideMembers, transactions);
    final Instant now = Instant.parse("2026-11-02T00:00:00Z");
    final List<RideSessionMember> saved = new ArrayList<>();
    RidingGroup group;
    String id;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        var approved = new ArrayList<GroupMember>();
        for (long i = 1; i <= 4; i++) {
            var user = new User(); user.setId(i); user.setNickname("user" + i);
            var member = new GroupMember(); member.setUser(user); approved.add(member);
        }
        group = new RidingGroup(); group.setId(10L); group.setOwner(approved.get(0).getUser());
        group.setGroupName("group");
        when(groups.findById(10L)).thenReturn(Optional.of(group));
        when(groups.findByIdForUpdate(10L)).thenReturn(Optional.of(group));
        when(members.existsByGroup_IdAndUser_IdAndStatus(10L, 1L, "APPROVED")).thenReturn(true);
        when(members.findApprovedMembers(10L)).thenReturn(approved);
        when(online.isOnline(anyLong())).thenReturn(true);
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    ((TransactionDefinition) call.getArgument(0)).getPropagationBehavior());
            return new SimpleTransactionStatus();
        });
        when(rides.save(any())).thenAnswer(call -> { RideSession ride = call.getArgument(0); ride.setId(50L); return ride; });
        when(rideMembers.save(any())).thenAnswer(call -> { saved.add(call.getArgument(0)); return call.getArgument(0); });
        id = service.create(1L, new CreateRideInvitationRequest(10L, List.of(2L, 3L, 4L))).invitationId();
        service.respond(2L, id, true);
        service.respond(3L, id, false);
        clearInvocations(online);
    }

    @Test void savesOnlyAcceptedAndNotifiesAfterCommitAndRemovalEvenWhenOffline() {
        AtomicBoolean committed = new AtomicBoolean();
        doAnswer(call -> { assertTrue(service.findActive(id).isPresent()); committed.set(true); return null; })
                .when(transactions).commit(any());
        when(online.isOnline(anyLong())).thenReturn(false);
        doAnswer(call -> {
            assertTrue(committed.get()); assertTrue(service.findActive(id).isEmpty());
            assertEquals(json.readTree("{\"type\":\"RIDE_STARTED\",\"rideSessionId\":50,\"groupId\":10}"),
                    json.readTree((String) call.getArgument(1)));
            return null;
        }).when(online).sendToUser(anyLong(), anyString());
        var response = service.start(1L, id);
        assertEquals(50L, response.rideSessionId());
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), response.startedAt());
        assertEquals(List.of(1L, 2L), saved.stream().map(m -> m.getUser().getId()).toList());
        assertEquals(List.of("LEADER", "MEMBER"), saved.stream().map(RideSessionMember::getRole).toList());
        var ride = saved.get(0).getRideSession();
        assertEquals("RIDING", ride.getStatus()); assertEquals(now, ride.getStartedAt());
        assertEquals(now, ride.getCreatedAt()); assertEquals(1L, ride.getLeader().getId()); assertNull(ride.getEndedAt());
        for (var member : saved) {
            assertSame(ride, member.getRideSession()); assertEquals(now, member.getJoinedAt());
            assertNull(member.getDistance()); assertNull(member.getAverageSpeed());
        }
        verify(online).sendToUser(eq(1L), anyString()); verify(online).sendToUser(eq(2L), anyString());
        verifyNoMoreInteractions(online);
        assertCode("INVITATION_NOT_FOUND", () -> service.start(1L, id));
        service.replay(1L, mock(org.springframework.web.socket.WebSocketSession.class));
        verifyNoMoreInteractions(online);
    }

    @Test void rejectsMissingExpiredNonCreatorAndNonOwner() {
        assertCode("INVITATION_NOT_FOUND", () -> service.start(1L, "missing"));
        assertCode("FORBIDDEN", () -> service.start(2L, id));
        var other = new User(); other.setId(9L); group.setOwner(other);
        assertCode("FORBIDDEN", () -> service.start(1L, id));
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        assertCode("INVITATION_NOT_FOUND", () -> service.start(1L, id));
        verifyNoInteractions(rides, rideMembers, online);
    }

    @Test void rejectsUnapprovedLeaderOrParticipantAndInsufficientCount() {
        when(members.existsByGroup_IdAndUser_IdAndStatus(10L, 1L, "APPROVED")).thenReturn(false);
        assertCode("FORBIDDEN", () -> service.start(1L, id));
        when(members.existsByGroup_IdAndUser_IdAndStatus(10L, 1L, "APPROVED")).thenReturn(true);
        var original = members.findApprovedMembers(10L);
        when(members.findApprovedMembers(10L)).thenReturn(original.subList(0, 1));
        assertCode("INVALID_PARTICIPANT", () -> service.start(1L, id));
        when(members.findApprovedMembers(10L)).thenReturn(original);
        String pending = service.create(1L, new CreateRideInvitationRequest(10L, List.of(2L))).invitationId();
        clearInvocations(online);
        assertCode("INSUFFICIENT_PARTICIPANTS", () -> service.start(1L, pending));
        assertTrue(service.findActive(id).isPresent());
        verifyNoInteractions(rides, rideMembers, online);
    }

    @Test void saveFailureRollsBackAndKeepsInvitationForRetry() {
        when(rideMembers.save(any())).thenThrow(new IllegalStateException("save failure"));
        assertThrows(IllegalStateException.class, () -> service.start(1L, id));
        verify(transactions).rollback(any()); verify(transactions, never()).commit(any());
        assertTrue(service.findActive(id).isPresent()); verifyNoInteractions(online);
        doAnswer(call -> call.getArgument(0)).when(rideMembers).save(any());
        assertEquals(50L, service.start(1L, id).rideSessionId());
    }

    @Test void commitFailureKeepsInvitationAndDoesNotNotify() {
        doThrow(new org.springframework.transaction.TransactionSystemException("commit failure"))
                .when(transactions).commit(any());
        assertThrows(org.springframework.transaction.TransactionSystemException.class, () -> service.start(1L, id));
        assertTrue(service.findActive(id).isPresent()); verifyNoInteractions(online);
        doNothing().when(transactions).commit(any());
        service.start(1L, id);
        assertTrue(service.findActive(id).isEmpty());
    }

    @Test void concurrentStartsCreateOnlyOneSession() throws Exception {
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<String> start = () -> { gate.await(); try { service.start(1L, id); return "OK"; }
            catch (ApiException e) { return e.getCode(); } };
        try {
            var a = pool.submit(start); var b = pool.submit(start); gate.countDown();
            assertEquals(Set.of("OK", "INVITATION_NOT_FOUND"), Set.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS)));
            verify(rides, times(1)).save(any()); verify(transactions, times(1)).commit(any());
            verify(online, times(2)).sendToUser(anyLong(), anyString());
        } finally { pool.shutdownNow(); }
    }

    @Test void responseAndCleanupWaitUntilCommitAndCannotChangeSnapshot() throws Exception {
        var committing = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { committing.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; })
                .when(transactions).commit(any());
        var pool = Executors.newFixedThreadPool(3);
        try {
            var start = pool.submit(() -> service.start(1L, id));
            assertTrue(committing.await(5, TimeUnit.SECONDS));
            var reply = pool.submit(() -> assertCode("INVITATION_NOT_FOUND", () -> service.respond(4L, id, true)));
            var cleanup = pool.submit(service::removeExpiredInvitations);
            release.countDown(); start.get(5, TimeUnit.SECONDS); reply.get(5, TimeUnit.SECONDS); cleanup.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1L, 2L), saved.stream().map(m -> m.getUser().getId()).toList());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void cancelRemovesInvitationNotifiesEveryStatusAndNeverWritesDatabase() {
        when(online.isOnline(anyLong())).thenReturn(false);
        clearInvocations(groups, members, rides, rideMembers, transactions);
        var result = service.cancel(1L, id);
        assertEquals(id, result.invitationId());
        assertEquals("CANCELLED", result.status());
        String expected = json.writeValueAsString(new com.ridesync.server.dto.RideInviteCancelledMessage(
                "RIDE_INVITE_CANCELLED", id, 10L));
        for (long user = 1; user <= 4; user++) verify(online).sendToUser(user, expected);
        verifyNoMoreInteractions(online);
        assertTrue(service.findActive(id).isEmpty());
        assertCode("INVITATION_NOT_FOUND", () -> service.cancel(1L, id));
        assertCode("INVITATION_NOT_FOUND", () -> service.start(1L, id));
        assertCode("INVITATION_NOT_FOUND", () -> service.respond(4L, id, true));
        assertCode("INVITATION_NOT_FOUND", () -> service.respond(4L, id, false));
        for (long user = 1; user <= 4; user++)
            service.replay(user, mock(org.springframework.web.socket.WebSocketSession.class));
        verifyNoMoreInteractions(online);
        verifyNoInteractions(groups, members, rides, rideMembers, transactions);
    }

    @Test void cancelRejectsNonCreatorMissingAndExpiredWithoutNotifications() {
        assertCode("FORBIDDEN", () -> service.cancel(2L, id));
        assertCode("FORBIDDEN", () -> service.cancel(99L, id));
        assertTrue(service.findActive(id).isPresent());
        assertCode("INVITATION_NOT_FOUND", () -> service.cancel(1L, "missing"));
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        assertCode("INVITATION_NOT_FOUND", () -> service.cancel(1L, id));
        verifyNoInteractions(online, rides, rideMembers);
    }

    @Test void cancelWorksWithoutAcceptedInviteesAndAfterFailedStart() {
        String pending = service.create(1L, new CreateRideInvitationRequest(10L, List.of(2L))).invitationId();
        assertEquals("CANCELLED", service.cancel(1L, pending).status());
        doThrow(new org.springframework.transaction.TransactionSystemException("commit failure"))
                .when(transactions).commit(any());
        assertThrows(org.springframework.transaction.TransactionSystemException.class, () -> service.start(1L, id));
        assertEquals("CANCELLED", service.cancel(1L, id).status());
    }

    @Test void cancelWaitsForStartCommitAndDoesNotCancelStartedRide() throws Exception {
        var committing = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { committing.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; })
                .when(transactions).commit(any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = pool.submit(() -> service.start(1L, id));
            assertTrue(committing.await(5, TimeUnit.SECONDS));
            var cancel = pool.submit(() -> assertCode("INVITATION_NOT_FOUND", () -> service.cancel(1L, id)));
            release.countDown(); start.get(5, TimeUnit.SECONDS); cancel.get(5, TimeUnit.SECONDS);
            verify(online, never()).sendToUser(anyLong(), contains("RIDE_INVITE_CANCELLED"));
            verify(rides, times(1)).save(any());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void cancellationBlocksConcurrentStartResponseReplayAndDuplicateCancel() throws Exception {
        var notifying = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { notifying.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; })
                .when(online).sendToUser(eq(1L), contains("RIDE_INVITE_CANCELLED"));
        var pool = Executors.newFixedThreadPool(5);
        try {
            var cancel = pool.submit(() -> service.cancel(1L, id));
            assertTrue(notifying.await(5, TimeUnit.SECONDS));
            var start = pool.submit(() -> assertCode("INVITATION_NOT_FOUND", () -> service.start(1L, id)));
            var response = pool.submit(() -> assertCode("INVITATION_NOT_FOUND", () -> service.respond(4L, id, true)));
            var duplicate = pool.submit(() -> assertCode("INVITATION_NOT_FOUND", () -> service.cancel(1L, id)));
            var replay = pool.submit(() -> service.replay(4L, mock(org.springframework.web.socket.WebSocketSession.class)));
            for (var future : List.of(start, response, duplicate, replay)) future.get(5, TimeUnit.SECONDS);
            release.countDown(); cancel.get(5, TimeUnit.SECONDS);
            verify(online, times(4)).sendToUser(anyLong(), contains("RIDE_INVITE_CANCELLED"));
            verifyNoMoreInteractions(online);
            verifyNoInteractions(rides, rideMembers);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void realRegistryDeliversCancellationToMultipleConnectionsDespiteBrokenSession() throws Exception {
        var registry = new OnlineUserRegistry();
        var sessions = new ArrayList<org.springframework.web.socket.WebSocketSession>();
        for (int i = 0; i < 6; i++) {
            var session = mock(org.springframework.web.socket.WebSocketSession.class);
            when(session.getId()).thenReturn("cancel-" + i); when(session.isOpen()).thenReturn(true);
            registry.register(i < 3 ? 1L : (long) i - 1, session, Instant.now().plusSeconds(300));
            sessions.add(session);
        }
        var actual = new RideInvitationService(groups, members, registry, json, clock, rides, rideMembers, transactions);
        String target = actual.create(1L, new CreateRideInvitationRequest(10L, List.of(2L, 3L, 4L))).invitationId();
        actual.respond(2L, target, true); actual.respond(3L, target, false);
        for (var session : sessions) clearInvocations(session);
        doThrow(new java.io.IOException("broken")).when(sessions.get(0)).sendMessage(any());
        actual.cancel(1L, target);
        var expected = new org.springframework.web.socket.TextMessage(json.writeValueAsString(
                new com.ridesync.server.dto.RideInviteCancelledMessage("RIDE_INVITE_CANCELLED", target, 10L)));
        for (int i = 1; i < sessions.size(); i++) {
            verify(sessions.get(i)).sendMessage(expected);
            verify(sessions.get(i), never()).close(any());
        }
        verify(sessions.get(0)).close(org.springframework.web.socket.CloseStatus.SERVER_ERROR);
        assertTrue(actual.findActive(target).isEmpty());
        assertTrue(registry.isOnline(1L));
        verifyNoInteractions(rides, rideMembers);
    }

    void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(ApiException.class, action::run).getCode());
    }
}
