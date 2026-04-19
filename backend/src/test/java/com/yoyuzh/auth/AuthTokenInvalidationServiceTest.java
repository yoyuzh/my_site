package com.yoyuzh.auth;

import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.boot.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenInvalidationServiceTest {

    @Test
    void shouldStoreAccessRevocationCutoffInEpochSeconds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AuthTokenInvalidationService service = new AuthTokenInvalidationService(
                redisTemplate,
                redisProperties(),
                jwtProperties()
        );

        service.revokeAccessTokensForUser(7L, AuthClientType.DESKTOP);

        verify(valueOperations).set(
                eq("yoyuzh:auth:access-revoked-before:7:DESKTOP"),
                any(String.class),
                eq(Duration.ofSeconds(960))
        );
    }

    @Test
    void shouldNotTreatSameSecondFreshTokenAsRevoked() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("yoyuzh:auth:access-revoked-before:7:DESKTOP")).thenReturn("1710000000");

        AuthTokenInvalidationService service = new AuthTokenInvalidationService(
                redisTemplate,
                redisProperties(),
                jwtProperties()
        );

        assertThat(service.isAccessTokenRevoked(
                7L,
                AuthClientType.DESKTOP,
                Instant.ofEpochSecond(1710000000L)
        )).isFalse();
    }

    @Test
    void shouldRemainCompatibleWithOldMillisecondRevocationValues() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("yoyuzh:auth:access-revoked-before:7:DESKTOP")).thenReturn("1710000000123");

        AuthTokenInvalidationService service = new AuthTokenInvalidationService(
                redisTemplate,
                redisProperties(),
                jwtProperties()
        );

        assertThat(service.isAccessTokenRevoked(
                7L,
                AuthClientType.DESKTOP,
                Instant.ofEpochSecond(1709999999L)
        )).isTrue();
        assertThat(service.isAccessTokenRevoked(
                7L,
                AuthClientType.DESKTOP,
                Instant.ofEpochSecond(1710000000L)
        )).isFalse();
    }

    private AppRedisProperties redisProperties() {
        AppRedisProperties properties = new AppRedisProperties();
        properties.setTtlBufferSeconds(60);
        return properties;
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessExpirationSeconds(900);
        return properties;
    }
}
