package com.ridesync.server.websocket;

import com.ridesync.server.dto.WebSocketErrorMessage;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.service.RideInvitationService;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.JacksonException;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@lombok.RequiredArgsConstructor
public class LoginWebSocketHandler extends TextWebSocketHandler {
    private final OnlineUserRegistry onlineUsers;
    private final RideInvitationService invitations;
    private final JsonMapper json;
    public static final String USER_ID_ATTRIBUTE = "userId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!(session.getPrincipal() instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long userId)
                || !(authentication.getDetails() instanceof java.time.Instant expiresAt)
                || !java.time.Instant.now().isBefore(expiresAt)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 쿼리나 메시지의 사용자 ID 대신 인증된 principal만 사용한다.
        session.getAttributes().put(USER_ID_ATTRIBUTE, userId);
        onlineUsers.register(userId, session, expiresAt);
        invitations.replay(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        onlineUsers.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        onlineUsers.disconnect(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, org.springframework.web.socket.PongMessage message) {
        onlineUsers.pong(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var userId = onlineUsers.authenticatedUser(session);
        if (userId.isEmpty()) return;
        try {
            var root = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(message.getPayload());
            if (root == null || !root.isObject() || !root.path("type").isString()) {
                throw invalidMessage();
            }
            if (!"RIDE_INVITE_RESPONSE".equals(root.path("type").asText())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MESSAGE_TYPE", "지원하지 않는 메시지 타입입니다.");
            }
            if (!root.path("invitationId").isString() || root.path("invitationId").asText().isBlank()
                    || !root.path("accepted").isBoolean()) {
                throw invalidMessage();
            }
            invitations.respond(userId.get(), root.path("invitationId").asText(), root.path("accepted").asBoolean());
        } catch (JacksonException exception) {
            sendError(session, "INVALID_MESSAGE", "메시지 형식을 확인해주세요.");
        } catch (ApiException exception) {
            sendError(session, exception.getCode(), exception.getMessage());
        }
    }

    private ApiException invalidMessage() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "메시지 형식을 확인해주세요.");
    }

    private void sendError(WebSocketSession session, String code, String message) {
        onlineUsers.sendToSession(session, json.writeValueAsString(new WebSocketErrorMessage("ERROR", code, message)));
    }
}
