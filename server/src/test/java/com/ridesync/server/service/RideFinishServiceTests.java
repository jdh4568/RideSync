package com.ridesync.server.service;

import com.ridesync.server.entity.*;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.*;
import com.ridesync.server.websocket.RideConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RideFinishServiceTests {
    final RideSessionRepository rides = mock(RideSessionRepository.class);
    final RideSessionMemberRepository members = mock(RideSessionMemberRepository.class);
    final RideConnectionRegistry connections = mock(RideConnectionRegistry.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final RideFinishService service = new RideFinishService(rides,members,connections,JsonMapper.builder().build(),manager);
    RideSession ride;
    @BeforeEach void setup() {
        var owner = new User(); owner.setId(1L); ride = new RideSession(); ride.setId(10L); ride.setLeader(owner); ride.setStatus("RIDING");
        var member = new RideSessionMember(); member.setRole("LEADER");
        when(rides.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(members.findByRideSession_IdAndUser_Id(10L,1L)).thenReturn(Optional.of(member));
        when(connections.lifecycleLock(10L)).thenReturn(new Object());
        when(manager.getTransaction(any())).thenAnswer(invocation -> {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,((TransactionDefinition)invocation.getArgument(0)).getPropagationBehavior());
            return new SimpleTransactionStatus();
        });
    }
    @Test void notifiesOnlyAfterCommitAndRepeatDoesNotChangeTimeOrNotify() {
        var result = service.finish(1L,10L);
        var order = inOrder(manager,connections);
        order.verify(manager).commit(any()); order.verify(connections).finish(10L,"{\"type\":\"RIDE_FINISHED\",\"rideSessionId\":10}");
        assertEquals(result,service.finish(1L,10L)); verify(rides,times(1)).saveAndFlush(any());
        verify(connections,times(1)).finish(anyLong(),anyString());
    }
    @Test void commitFailureDoesNotNotifyOrClose() {
        doThrow(new TransactionSystemException("commit failure")).when(manager).commit(any());
        assertThrows(TransactionSystemException.class,()->service.finish(1L,10L));
        verify(connections,never()).finish(anyLong(),anyString());
    }
    @Test void readyAndNonLeaderAreRejectedWithoutSaving() {
        ride.setStatus("READY");
        assertEquals("RIDE_NOT_ACTIVE",assertThrows(ApiException.class,()->service.finish(1L,10L)).getCode());
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,()->service.finish(2L,10L)).getCode());
        verify(rides,never()).saveAndFlush(any()); verify(connections,never()).finish(anyLong(),anyString());
    }
}
