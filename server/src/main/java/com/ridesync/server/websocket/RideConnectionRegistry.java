package com.ridesync.server.websocket;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RideConnectionRegistry {
    // 고정 개수 잠금으로 세션 ID별 잠금 객체가 무한히 쌓이지 않게 한다.
    private final Object[] lifecycleLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new Object()).toArray();

    public Object lifecycleLock(Long rideId) {
        return lifecycleLocks[Math.floorMod(rideId.hashCode(), lifecycleLocks.length)];
    }

    // 호출자는 lifecycleLock을 커밋부터 알림·정리 완료까지 유지한다.
    public void finish(Long rideId, String payload) {
        for (Connection c : connections.values()) {
            if (!c.rideId.equals(rideId)) continue;
            sendToSession(c.session, payload);
            disconnect(c.session, CloseStatus.NORMAL);
        }
    }
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public record Participant(Long rideId, Long userId) {}

    private static class Connection {
        final Long userId;
        final Long rideId;
        final String role;
        final WebSocketSession session;
        final Instant expiresAt;
        volatile Instant lastPong;
        Instant lastPing;

        Connection(Long rideId, Long userId, String role, WebSocketSession session, Instant expiresAt, Instant now) {
            this.userId = userId;
            this.rideId = rideId;
            this.role = role;
            this.session = session;
            this.expiresAt = expiresAt;
            lastPong = now;
            lastPing = now;
        }

        boolean valid(Instant now) {
            return session.isOpen() && now.isBefore(expiresAt) && !now.isAfter(lastPong.plusSeconds(90));
        }
    }

    public void register(Long rideId, Long userId, String role, WebSocketSession session, Instant expiresAt) {
        connections.put(session.getId(), new Connection(rideId, userId, role, session, expiresAt, Instant.now()));
    }

    public void remove(WebSocketSession session) {
        connections.computeIfPresent(session.getId(), (id, c) -> c.session == session ? null : c);
    }

    public void sendToMembers(Long rideId, String payload) {
        for (Connection c : connections.values()) {
            if (!c.rideId.equals(rideId) || !"MEMBER".equals(c.role)) continue;
            sendToSession(c.session, payload);
        }
    }

    public Optional<Participant> authenticatedParticipant(WebSocketSession session) {
        Connection c = connections.get(session.getId());
        if (c == null || c.session != session) return Optional.empty();
        if (!c.valid(Instant.now())) {
            disconnect(session, CloseStatus.POLICY_VIOLATION);
            return Optional.empty();
        }
        return Optional.of(new Participant(c.rideId, c.userId));
    }

    public void sendToSession(WebSocketSession session, String payload) {
        Connection c = connections.get(session.getId());
        if (c == null || c.session != session) return;
        try {
            synchronized (c.session) {
                if (connections.get(c.session.getId()) != c) return;
                if (!c.valid(Instant.now())) {
                    disconnect(c.session, CloseStatus.POLICY_VIOLATION);
                    return;
                }
                c.session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception exception) {
            // 일부 기기의 실패가 같은 사용자 또는 다른 사용자의 알림을 막지 않는다.
            disconnect(c.session, CloseStatus.SERVER_ERROR);
        }
    }

    public void pong(WebSocketSession session) {
        Connection c = connections.get(session.getId());
        if (c != null && c.session == session) c.lastPong = Instant.now();
    }

    public void disconnect(WebSocketSession session, CloseStatus status) {
        remove(session);
        try {
            session.close(status);
        } catch (Exception ignored) {
            // 전송 계층이 이미 종료됐어도 메모리 정리는 완료한다.
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void checkConnections() {
        checkConnections(Instant.now());
    }

    void checkConnections(Instant now) {
        for (Connection c : connections.values()) {
            if (!c.valid(now)) {
                disconnect(c.session, CloseStatus.POLICY_VIOLATION);
            } else if (!now.isBefore(c.lastPing.plusSeconds(30))) {
                try {
                    synchronized (c.session) {
                        if (connections.get(c.session.getId()) != c) continue;
                        c.session.sendMessage(new PingMessage());
                        c.lastPing = now;
                    }
                } catch (Exception exception) {
                    disconnect(c.session, CloseStatus.SERVER_ERROR);
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Connection c : connections.values()) disconnect(c.session, CloseStatus.GOING_AWAY);
    }
}
