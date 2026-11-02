package com.ridesync.server.service;

import com.ridesync.server.entity.*;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RideServiceTests {
    final RideSessionRepository rides = mock(RideSessionRepository.class);
    final RideSessionMemberRepository members = mock(RideSessionMemberRepository.class);
    final RideService service = new RideService(rides, members);

    RideSession session() {
        var owner = new User(); owner.setId(1L);
        var group = new RidingGroup(); group.setGroupName("한강모임"); group.setOwner(owner);
        var ride = new RideSession(); ride.setId(10L); ride.setGroup(group); ride.setLeader(owner);
        when(rides.findById(10L)).thenReturn(Optional.of(ride));
        return ride;
    }

    @Test void leaderAndMemberReceiveGroupNameAndPersistedParticipantCount() {
        session();
        when(members.countByRideSession_Id(10L)).thenReturn(5L);
        for (long userId : new long[]{1L, 2L}) {
            when(members.existsByRideSession_IdAndUser_Id(10L, userId)).thenReturn(true);
            var result = service.getRide(userId, 10L);
            assertEquals("한강모임", result.groupName());
            assertEquals(5L, result.participantCount());
        }
    }

    @Test void nonParticipantsIncludingOwnerCannotReadSession() {
        session();
        for (long userId : new long[]{1L, 3L, 99L}) {
            var error = assertThrows(ApiException.class, () -> service.getRide(userId, 10L));
            assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
            assertEquals("FORBIDDEN", error.getCode());
        }
        verify(members, never()).countByRideSession_Id(anyLong());
    }

    @Test void membershipOfAnotherSessionDoesNotGrantAccess() {
        session();
        when(members.existsByRideSession_IdAndUser_Id(11L, 2L)).thenReturn(true);
        assertEquals("FORBIDDEN", assertThrows(ApiException.class, () -> service.getRide(2L, 10L)).getCode());
    }

    @Test void missingSessionReturnsNotFoundBeforeMembershipLookup() {
        var error = assertThrows(ApiException.class, () -> service.getRide(1L, 999L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("RIDE_SESSION_NOT_FOUND", error.getCode());
        verifyNoInteractions(members);
    }

    @Test void rideConnectionRequiresMembershipAndRidingStatus() {
        var ride = session(); ride.setStatus("RIDING");
        assertEquals("FORBIDDEN", assertThrows(ApiException.class,
                () -> service.requireRidingParticipant(1L, 10L)).getCode());
        var member = new RideSessionMember(); member.setRole("LEADER");
        when(members.findByRideSession_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));
        assertEquals("LEADER", service.requireRidingParticipant(1L, 10L));
        member.setRole("MEMBER"); assertEquals("MEMBER", service.requireRidingParticipant(1L, 10L));
        for (String state : new String[]{"READY", "FINISHED"}) {
            ride.setStatus(state);
            assertEquals("RIDE_NOT_ACTIVE", assertThrows(ApiException.class,
                    () -> service.requireRidingParticipant(1L, 10L)).getCode());
        }
        assertEquals("RIDE_SESSION_NOT_FOUND", assertThrows(ApiException.class,
                () -> service.requireRidingParticipant(1L, 999L)).getCode());
    }
}
