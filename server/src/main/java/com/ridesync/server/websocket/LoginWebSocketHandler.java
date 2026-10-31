package com.ridesync.server.websocket;

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
        // 접속 인증 단계에서는 초대 등 애플리케이션 메시지를 아직 지원하지 않는다.
        onlineUsers.disconnect(session, CloseStatus.NOT_ACCEPTABLE);
    }
}
