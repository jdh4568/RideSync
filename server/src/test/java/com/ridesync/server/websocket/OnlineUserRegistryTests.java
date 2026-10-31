package com.ridesync.server.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OnlineUserRegistryTests {
    @Test
    void textReachesAllValidUserConnectionsDespiteOneFailure() throws Exception {
        var registry = new OnlineUserRegistry();
        var a = session("a");
        var b = session("b");
        var broken = session("broken");
        var expired = session("expired");
        var other = session("other");
        for (var s : new WebSocketSession[]{a, b, broken}) {
            registry.register(1L, s, Instant.now().plusSeconds(300));
        }
        registry.register(1L, expired, Instant.now().minusSeconds(1));
        registry.register(2L, other, Instant.now().plusSeconds(300));
        doThrow(new java.io.IOException()).when(broken).sendMessage(any());
        registry.sendToUser(1L, "hello");
        verify(a).sendMessage(eq(new TextMessage("hello")));
        verify(b).sendMessage(eq(new TextMessage("hello")));
        verify(broken).close(CloseStatus.SERVER_ERROR);
        verify(expired, never()).sendMessage(any());
        verify(other, never()).sendMessage(any());
        registry.sendToUser(1L, "again");
        verify(broken, times(1)).sendMessage(any());
        assertTrue(registry.isOnline(1L));
    }

    private WebSocketSession session(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Test
    void removingOldSessionDoesNotRemoveNewConnection() {
        var registry = new OnlineUserRegistry();
        var old = session("old");
        var current = session("new");
        registry.register(1L, old, Instant.now().plusSeconds(300));
        registry.register(1L, current, Instant.now().plusSeconds(300));
        registry.remove(old);
        registry.remove(old);
        assertTrue(registry.isOnline(1L));
        assertFalse(registry.isOnline(2L));
        registry.remove(current);
        assertFalse(registry.isOnline(1L));
    }

    @Test
    void pingIsSentAndMissingPongExpiresConnection() throws Exception {
        var registry = new OnlineUserRegistry();
        var s = session("one");
        registry.register(1L, s, Instant.now().plusSeconds(300));
        registry.checkConnections(Instant.now().plusSeconds(31));
        verify(s).sendMessage(isA(PingMessage.class));
        registry.checkConnections(Instant.now().plusSeconds(91));
        assertFalse(registry.isOnline(1L));
        verify(s).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void expiredJwtClosesOnlyItsOwnSession() throws Exception {
        var registry = new OnlineUserRegistry();
        var expired = session("expired");
        var fresh = session("fresh");
        registry.register(1L, expired, Instant.now().minusSeconds(1));
        registry.register(1L, fresh, Instant.now().plusSeconds(300));
        registry.checkConnections();
        assertTrue(registry.isOnline(1L));
        verify(expired).close(CloseStatus.POLICY_VIOLATION);
        verify(fresh, never()).close(any());
    }

    @Test
    void failedPingAndFailedCloseStillRemoveConnection() throws Exception {
        var registry = new OnlineUserRegistry();
        var s = session("broken");
        doThrow(new java.io.IOException()).when(s).sendMessage(any());
        doThrow(new java.io.IOException()).when(s).close(any());
        registry.register(1L, s, Instant.now().plusSeconds(300));
        registry.checkConnections(Instant.now().plusSeconds(31));
        assertFalse(registry.isOnline(1L));
    }

    @Test
    void shutdownClearsAllUsers() {
        var registry = new OnlineUserRegistry();
        registry.register(1L, session("a"), Instant.now().plusSeconds(300));
        registry.register(2L, session("b"), Instant.now().plusSeconds(300));
        registry.shutdown();
        assertFalse(registry.isOnline(1L));
        assertFalse(registry.isOnline(2L));
    }
}
