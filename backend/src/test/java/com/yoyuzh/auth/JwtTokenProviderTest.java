package com.yoyuzh.auth;

import com.yoyuzh.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void shouldRejectEmptyJwtSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("   ");

        JwtTokenProvider provider = new JwtTokenProvider(properties);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    void shouldRejectDefaultJwtSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("change-me-change-me-change-me-change-me");

        JwtTokenProvider provider = new JwtTokenProvider(properties);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认 JWT 密钥");
    }

    @Test
    void shouldRejectTooShortJwtSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short-secret");

        JwtTokenProvider provider = new JwtTokenProvider(properties);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 32 字节");
    }

    @Test
    void shouldGenerateShortLivedAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setAccessExpirationSeconds(900);

        JwtTokenProvider provider = new JwtTokenProvider(properties);
        provider.init();

        String token = provider.generateAccessToken(7L, "alice", "session-1");
        SecretKey secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        Instant expiration = Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .toInstant();

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsername(token)).isEqualTo("alice");
        assertThat(provider.getUserId(token)).isEqualTo(7L);
        assertThat(provider.getSessionId(token)).isEqualTo("session-1");
        assertThat(provider.hasMatchingSession(token, "session-1")).isTrue();
        assertThat(provider.hasMatchingSession(token, "session-2")).isFalse();
        assertThat(expiration).isAfter(Instant.now().plusSeconds(850));
        assertThat(expiration).isBefore(Instant.now().plusSeconds(950));
    }
}
