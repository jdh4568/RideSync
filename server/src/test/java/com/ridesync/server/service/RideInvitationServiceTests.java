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
    final RidingGroupRepository groups = mock(RidingGroupRepository.class);
    final GroupMemberRepository members = mock(GroupMemberRepository.class);
    final OnlineUserRegistry online = mock(OnlineUserRegistry.class);
    final Clock clock = mock(Clock.class);
    final JsonMapper json = JsonMapper.builder().build();
    final RideInvitationService service = new RideInvitationService(groups, members, online, json, clock);
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
