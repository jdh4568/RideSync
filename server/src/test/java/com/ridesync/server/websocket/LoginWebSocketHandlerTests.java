package com.ridesync.server.websocket;

import com.ridesync.server.service.RideInvitationService;
import com.ridesync.server.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginWebSocketHandlerTests {
    final OnlineUserRegistry registry = new OnlineUserRegistry();
    final RideInvitationService invitations = mock(RideInvitationService.class);
    final JsonMapper json = JsonMapper.builder().build();
    final LoginWebSocketHandler handler = new LoginWebSocketHandler(registry, invitations, json);

    WebSocketSession connect(String id, long userId) throws Exception {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        auth.setDetails(Instant.now().plusSeconds(300));
        when(session.getPrincipal()).thenReturn(auth);
        handler.afterConnectionEstablished(session);
        return session;
    }

    @Test
    void registersThenReplaysAndIdentifiesResponseFromConnection() throws Exception {
        var session = connect("a", 2L);
        verify(invitations).replay(2L, session);
        assertEquals(2L, registry.authenticatedUser(session).orElseThrow());
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"inv-1\",\"accepted\":true,\"userId\":99}"));
        verify(invitations).respond(2L, "inv-1", true);
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"inv-2\",\"accepted\":false}"));
        verify(invitations).respond(2L, "inv-2", false);
    }

    @Test
    void malformedMessagesReturnErrorsOnlyToSenderAndKeepConnectionAlive() throws Exception {
        var sender = connect("a", 2L);
        var other = connect("b", 2L);
        clearInvocations(invitations);
        List<String> payloads = new ArrayList<>();
        doAnswer(call -> { payloads.add(((TextMessage) call.getArgument(0)).getPayload()); return null; })
                .when(sender).sendMessage(any(TextMessage.class));
        for (String input : List.of("{", "", "null", "[]", "{}", "{\"type\":4}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\"}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\" \" ,\"accepted\":true}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":1,\"accepted\":true}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"x\",\"accepted\":null}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"x\",\"accepted\":\"false\"}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"x\",\"accepted\":0}",
                "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"x\",\"accepted\":true} {}")) {
            handler.handleTextMessage(sender, new TextMessage(input));
            var error = json.readTree(payloads.get(payloads.size() - 1));
            assertEquals("ERROR", error.get("type").asText());
            assertEquals("INVALID_MESSAGE", error.get("code").asText());
        }
        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"UNKNOWN\"}"));
        assertEquals("UNSUPPORTED_MESSAGE_TYPE", json.readTree(payloads.get(payloads.size() - 1)).get("code").asText());
        verify(other, never()).sendMessage(any());
        verify(sender, never()).close(any());
        verifyNoInteractions(invitations);
        assertTrue(registry.isOnline(2L));
    }

    @Test
    void businessErrorsAreStructuredAndExpiredOrRemovedSessionsCannotRespond() throws Exception {
        var session = connect("a", 2L);
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "없는 초대"))
                .when(invitations).respond(2L, "missing", true);
        String input = "{\"type\":\"RIDE_INVITE_RESPONSE\",\"invitationId\":\"missing\",\"accepted\":true}";
        handler.handleTextMessage(session, new TextMessage(input));
        var sent = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertEquals("INVITATION_NOT_FOUND", json.readTree(sent.getValue().getPayload()).get("code").asText());
        clearInvocations(invitations);
        registry.register(2L, session, Instant.now().minusSeconds(1));
        handler.handleTextMessage(session, new TextMessage(input));
        handler.handleTextMessage(session, new TextMessage(input));
        verifyNoInteractions(invitations);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }
}
