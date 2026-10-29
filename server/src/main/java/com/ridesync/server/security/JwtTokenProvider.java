package com.ridesync.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {
    // 로컬 텀프로젝트용 공개 기본키. 실제 서비스에서는 JWT_SECRET으로 교체한다.
    private static final String LOCAL_DEV_SECRET =
            "cmlkZW1hdGUtbG9jYWwtZGV2ZWxvcG1lbnQta2V5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg==";
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtTokenProvider(@Value("${JWT_SECRET:" + LOCAL_DEV_SECRET + "}") String secret,
                            @Value("${JWT_EXPIRATION_SECONDS:3600}") long expirationSeconds) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION_SECONDS는 양수여야 합니다.");
        }
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null) {
            throw new JwtException("토큰 만료 시각이 없습니다.");
        }
        try {
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0) {
                throw new NumberFormatException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new JwtException("유효하지 않은 사용자 ID입니다.", exception);
        }
    }
}
