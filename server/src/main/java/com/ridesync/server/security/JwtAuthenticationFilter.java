package com.ridesync.server.security;

import com.ridesync.server.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        java.time.Instant expiresAt = null;
        try {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new JwtException("Bearer 토큰이 필요합니다.");
            }
            userId = tokenProvider.getUserId(header.substring(7).trim());
            if (request.getServletPath().equals("/ws/login")) {
                expiresAt = tokenProvider.getExpiration(header.substring(7).trim());
            }
        } catch (JwtException | IllegalArgumentException exception) {
            reject(response);
            return;
        }
        if (!userRepository.existsById(userId)) {
            reject(response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        authentication.setDetails(expiresAt);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"유효하지 않거나 만료된 토큰입니다.\"}");
    }
}
