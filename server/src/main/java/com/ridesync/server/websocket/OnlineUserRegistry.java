package com.ridesync.server.websocket;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineUserRegistry {
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    private static class Connection {
        final Long userId;
        final WebSocketSession session;
        final Instant expiresAt;
        volatile Instant lastPong;
        Instant lastPing;

        Connection(Long userId, WebSocketSession session, Instant expiresAt, Instant now) {
            this.userId = userId;
            this.session = session;
            this.expiresAt = expiresAt;
            lastPong = now;
            lastPing = now;
        }

        boolean valid(Instant now) {
            return session.isOpen() && now.isBefore(expiresAt) && !now.isAfter(lastPong.plusSeconds(90));
        }
    }

    public void register(Long userId, WebSocketSession session, Instant expiresAt) {
        connections.put(session.getId(), new Connection(userId, session, expiresAt, Instant.now()));
    }

    public boolean isOnline(Long userId) {
        Instant now = Instant.now();
        return connections.values().stream().anyMatch(c -> c.userId.equals(userId) && c.valid(now));
    }

    public void remove(WebSocketSession session) {
        connections.computeIfPresent(session.getId(), (id, c) -> c.session == session ? null : c);
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
                        c.session.sendMessage(new PingMessage());
                    }
                    c.lastPing = now;
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
