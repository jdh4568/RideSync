package com.ridesync.server.websocket;

import com.ridesync.server.dto.RideSignalMessage;
import com.ridesync.server.dto.WebSocketErrorMessage;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RideWebSocketHandler extends TextWebSocketHandler {
    private static final Set<String> SIGNALS = Set.of("LEFT", "RIGHT", "STOP", "SLOW", "ROAD_HAZARD",
            "SINGLE_FILE", "DOUBLE_FILE", "LEADER_CHANGE");
    private final RideService rides;
    private final RideConnectionRegistry connections;
    private final JsonMapper json;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!(session.getPrincipal() instanceof Authentication auth) || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof Long userId)
                || !(auth.getDetails() instanceof Instant expiresAt) || !Instant.now().isBefore(expiresAt)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            String path = session.getUri().getPath();
            long rideId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            if (rideId <= 0) throw new NumberFormatException();
            synchronized (connections.lifecycleLock(rideId)) {
                String role = rides.requireRidingParticipant(userId, rideId);
                connections.register(rideId, userId, role, session, expiresAt);
            }
        } catch (NumberFormatException exception) {
            reject(session, "INVALID_REQUEST", "라이딩 세션 ID를 확인해주세요.");
        } catch (ApiException exception) {
            reject(session, exception.getCode(), exception.getMessage());
        }
    }

    private void reject(WebSocketSession session, String code, String message) throws Exception {
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(new WebSocketErrorMessage("ERROR", code, message))));
        } finally { session.close(CloseStatus.POLICY_VIOLATION); }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var participant = connections.authenticatedParticipant(session);
        if (participant.isEmpty()) return;
        var sender = participant.get();
        synchronized (connections.lifecycleLock(sender.rideId())) {
            if (connections.authenticatedParticipant(session).isEmpty()) return;
            try {
                var root = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(message.getPayload());
                if (root == null || !root.isObject() || !root.path("type").isString()) throw invalidMessage();
                if (!"RIDE_SIGNAL".equals(root.path("type").asText())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MESSAGE_TYPE", "지원하지 않는 메시지 타입입니다.");
                }
                if (!root.path("signal").isString() || !SIGNALS.contains(root.path("signal").asText())) throw invalidMessage();
                // 접속 이후에도 DB의 세션 상태 및 발신자 역할을 확인한다.
                if (!"LEADER".equals(rides.requireRidingParticipant(sender.userId(), sender.rideId()))) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "선두만 수신호를 전송할 수 있습니다.");
                }
                connections.sendToMembers(sender.rideId(), json.writeValueAsString(
                        new RideSignalMessage("RIDE_SIGNAL", sender.userId(), root.path("signal").asText())));
            } catch (JacksonException exception) {
                sendError(session, "INVALID_MESSAGE", "메시지 형식을 확인해주세요.");
            } catch (ApiException exception) {
                sendError(session, exception.getCode(), exception.getMessage());
                if ("RIDE_NOT_ACTIVE".equals(exception.getCode()) || "RIDE_SESSION_NOT_FOUND".equals(exception.getCode())) {
                    connections.disconnect(session, CloseStatus.POLICY_VIOLATION);
                }
            }
        }

    }

    private ApiException invalidMessage() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "메시지 형식을 확인해주세요.");
    }

    private void sendError(WebSocketSession session, String code, String message) {
        connections.sendToSession(session, json.writeValueAsString(new WebSocketErrorMessage("ERROR", code, message)));
    }

    @Override protected void handlePongMessage(WebSocketSession session, PongMessage message) { connections.pong(session); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { connections.remove(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) { connections.disconnect(session, CloseStatus.SERVER_ERROR); }
}
