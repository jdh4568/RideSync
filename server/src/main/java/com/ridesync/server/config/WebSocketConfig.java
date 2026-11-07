package com.ridesync.server.config;

import com.ridesync.server.websocket.LoginWebSocketHandler;
import com.ridesync.server.websocket.RideWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@org.springframework.scheduling.annotation.EnableScheduling
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final LoginWebSocketHandler loginHandler;
    private final RideWebSocketHandler rideHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 최초 HTTP 연결 요청은 기존 SecurityFilterChain의 JWT 인증을 거친다.
        registry.addHandler(loginHandler, "/ws/login");
        registry.addHandler(rideHandler, "/ws/ride/{rideSessionId}");
    }
}
