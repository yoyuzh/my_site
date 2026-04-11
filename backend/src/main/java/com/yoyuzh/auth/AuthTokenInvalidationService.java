package com.yoyuzh.auth;

import com.yoyuzh.config.AppRedisProperties;
import com.yoyuzh.config.JwtProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class AuthTokenInvalidationService {

    private final StringRedisTemplate redisTemplate;
    private final AppRedisProperties redisProperties;
    private final JwtProperties jwtProperties;

    public AuthTokenInvalidationService(StringRedisTemplate redisTemplate,
                                        AppRedisProperties redisProperties,
                                        JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.jwtProperties = jwtProperties;
    }

    public void revokeAccessTokensForUser(Long userId) {
        revokeAccessTokensForUser(userId, AuthClientType.DESKTOP);
        revokeAccessTokensForUser(userId, AuthClientType.MOBILE);
    }

    public void revokeAccessTokensForUser(Long userId, AuthClientType clientType) {
        if (userId == null || clientType == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                buildAccessInvalidationKey(userId, clientType),
                Long.toString(Instant.now().getEpochSecond()),
                Duration.ofSeconds(jwtProperties.getAccessExpirationSeconds() + redisProperties.getTtlBufferSeconds())
        );
    }

    public boolean isAccessTokenRevoked(Long userId, AuthClientType clientType, Instant issuedAt) {
        if (userId == null || clientType == null || issuedAt == null) {
            return false;
        }
        String rawValue = redisTemplate.opsForValue().get(buildAccessInvalidationKey(userId, clientType));
        if (!StringUtils.hasText(rawValue)) {
            return false;
        }
        long revokedBeforeEpochSecond = normalizeRevokedBefore(rawValue);
        if (revokedBeforeEpochSecond <= 0L) {
            return false;
        }
        return issuedAt.getEpochSecond() < revokedBeforeEpochSecond;
    }

    public void blacklistRefreshTokenHash(String tokenHash, Instant expiresAt) {
        if (!StringUtils.hasText(tokenHash) || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt)
                .plusSeconds(redisProperties.getTtlBufferSeconds());
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(redisProperties.getTtlBufferSeconds());
        }
        redisTemplate.opsForValue().set(buildRefreshTokenBlacklistKey(tokenHash), "1", ttl);
    }

    public boolean isRefreshTokenHashBlacklisted(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildRefreshTokenBlacklistKey(tokenHash)));
    }

    private String buildAccessInvalidationKey(Long userId, AuthClientType clientType) {
        return buildAuthKey("access-revoked-before", userId.toString(), clientType.name());
    }

    private String buildRefreshTokenBlacklistKey(String tokenHash) {
        return buildAuthKey("refresh-blacklist", tokenHash);
    }

    private long normalizeRevokedBefore(String rawValue) {
        long parsed = Long.parseLong(rawValue);
        if (parsed > 9_999_999_999L) {
            return parsed / 1000L;
        }
        return parsed;
    }

    private String buildAuthKey(String... segments) {
        StringBuilder builder = new StringBuilder();
        builder.append(redisProperties.getKeyPrefix())
                .append(':')
                .append(redisProperties.getNamespaces().getAuth());
        for (String segment : segments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            builder.append(':').append(segment.trim());
        }
        return builder.toString();
    }
}
