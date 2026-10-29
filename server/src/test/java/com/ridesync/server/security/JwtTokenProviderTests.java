package com.ridesync.server.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTests {
    // 테스트 전용 키이며 서버 설정에는 사용하지 않는다.
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);

    @Test
    void noEnvironmentSettingsUsesStableDevelopmentKey() {
        try (var first = context(Map.of()); var second = context(Map.of())) {
            var firstProvider = first.getBean(JwtTokenProvider.class);
            var secondProvider = second.getBean(JwtTokenProvider.class);
            assertEquals(10L, secondProvider.getUserId(firstProvider.createToken(10L)));
        }
    }

    @Test
    void explicitSettingsOverrideDevelopmentDefaults() {
        try (var context = context(Map.of("JWT_SECRET", SECRET, "JWT_EXPIRATION_SECONDS", "120"))) {
            String token = context.getBean(JwtTokenProvider.class).createToken(10L);
            assertEquals(10L, provider.getUserId(token));
            var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                    .build().parseSignedClaims(token).getPayload();
            assertEquals(120000, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        var sources = context.getEnvironment().getPropertySources();
        // 실행 PC의 환경변수와 무관하게 기본값/명시적 설정을 각각 검증한다.
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("test", properties));
        context.register(JwtTokenProvider.class);
        context.refresh();
        return context;
    }

    @Test
    void tokenContainsIdAndOneHourLifetime() {
        String token = provider.createToken(10L);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals(10L, provider.getUserId(token));
        assertEquals(3600000, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertEquals(3, claims.size());
    }

    @Test
    void missingExpiryIsRejected() {
        String token = Jwts.builder().subject("10")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
        assertThrows(JwtException.class, () -> provider.getUserId(token));
    }

    @Test
    void invalidSubjectsAreRejected() {
        for (String subject : new String[]{null, "not-an-id", "0", "-1"}) {
            String token = Jwts.builder().subject(subject).expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
            assertThrows(JwtException.class, () -> provider.getUserId(token));
        }
    }

    @Test
    void invalidConfigurationFailsEarly() {
        assertThrows(RuntimeException.class, () -> new JwtTokenProvider("short", 3600));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider(SECRET, 0));
    }
}
