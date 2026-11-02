package com.ridesync.server.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RideConnectionRegistryTests {
    WebSocketSession session(String id) {
        var s = mock(WebSocketSession.class); when(s.getId()).thenReturn(id); when(s.isOpen()).thenReturn(true); return s;
    }
    @Test void isolatesRidesAndRolesAndContinuesAfterFailure() throws Exception {
        var registry = new RideConnectionRegistry(); var a = session("a"); var b = session("b");
        var broken = session("broken"); var leader = session("leader"); var other = session("other");
        for (var s : new WebSocketSession[]{a,b,broken}) registry.register(10L, 2L, "MEMBER", s, Instant.now().plusSeconds(300));
        registry.register(10L, 1L, "LEADER", leader, Instant.now().plusSeconds(300));
        registry.register(20L, 2L, "MEMBER", other, Instant.now().plusSeconds(300));
        doThrow(new java.io.IOException()).when(broken).sendMessage(any());
        registry.sendToMembers(10L, "hello");
        verify(a).sendMessage(new TextMessage("hello")); verify(b).sendMessage(new TextMessage("hello"));
        verify(leader, never()).sendMessage(any()); verify(other, never()).sendMessage(any());
        verify(broken).close(CloseStatus.SERVER_ERROR); assertTrue(registry.authenticatedParticipant(broken).isEmpty());
        registry.remove(a); assertTrue(registry.authenticatedParticipant(b).isPresent());
        registry.shutdown(); assertTrue(registry.authenticatedParticipant(b).isEmpty());
    }
    @Test void expiresJwtAndMissingPongAndSendsPing() throws Exception {
        var registry = new RideConnectionRegistry(); var s = session("s"); var expired = session("expired");
        registry.register(10L, 1L, "MEMBER", s, Instant.now().plusSeconds(300));
        registry.register(10L, 1L, "MEMBER", expired, Instant.now().minusSeconds(1));
        registry.checkConnections(Instant.now().plusSeconds(31));
        verify(s).sendMessage(isA(PingMessage.class)); verify(expired).close(CloseStatus.POLICY_VIOLATION);
        registry.checkConnections(Instant.now().plusSeconds(91));
        verify(s).close(CloseStatus.POLICY_VIOLATION); assertTrue(registry.authenticatedParticipant(s).isEmpty());
    }
    @Test void pongRefreshesLivenessAndFailedCloseStillRemoves() throws Exception {
        var registry = new RideConnectionRegistry(); var s = session("s");
        registry.register(10L, 1L, "MEMBER", s, Instant.now().plusSeconds(300));
        registry.pong(s); assertTrue(registry.authenticatedParticipant(s).isPresent());
        doThrow(new java.io.IOException()).when(s).sendMessage(any());
        doThrow(new java.io.IOException()).when(s).close(any());
        registry.checkConnections(Instant.now().plusSeconds(31));
        assertTrue(registry.authenticatedParticipant(s).isEmpty());
    }
}
