package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.infra.cache.AppRedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class AuthTokenInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenInvalidationService.class);

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
        revokeAccessTokensForUser(userId, IdentityClientType.DESKTOP);
        revokeAccessTokensForUser(userId, IdentityClientType.MOBILE);
    }

    public void revokeAccessTokensForUser(Long userId, IdentityClientType clientType) {
        if (userId == null || clientType == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                buildAccessInvalidationKey(userId, clientType),
                Long.toString(Instant.now().getEpochSecond()),
                Duration.ofSeconds(jwtProperties.getAccessExpirationSeconds() + redisProperties.getTtlBufferSeconds())
        );
    }

    public boolean isAccessTokenRevoked(Long userId, IdentityClientType clientType, Instant issuedAt) {
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

    private String buildAccessInvalidationKey(Long userId, IdentityClientType clientType) {
        return buildAuthKey("access-revoked-before", userId.toString(), clientType.name());
    }

    private String buildRefreshTokenBlacklistKey(String tokenHash) {
        return buildAuthKey("refresh-blacklist", tokenHash);
    }

    private long normalizeRevokedBefore(String rawValue) {
        try {
            long parsed = Long.parseLong(rawValue.trim());
            if (parsed > 9_999_999_999L) {
                return parsed / 1000L;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("Ignoring malformed auth revocation cutoff value: {}", rawValue);
            return 0L;
        }
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
