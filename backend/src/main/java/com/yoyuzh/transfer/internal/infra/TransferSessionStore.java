package com.yoyuzh.transfer.internal.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.transfer.internal.domain.TransferSession;
import com.yoyuzh.transfer.internal.domain.TransferSessionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TransferSessionStore {

    private static final String RESERVED_PICKUP_CODE = "__reserved__";
    private static final Duration SESSION_LOCK_TTL = Duration.ofSeconds(5);

    private final Map<String, TransferSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdsByPickupCode = new ConcurrentHashMap<>();
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AppRedisProperties redisProperties;
    private final DistributedLockGateway distributedLockGateway;

    @Autowired
    public TransferSessionStore(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                ObjectMapper objectMapper,
                                AppRedisProperties redisProperties,
                                ObjectProvider<DistributedLockGateway> distributedLockGatewayProvider) {
        this(
                stringRedisTemplateProvider.getIfAvailable(),
                objectMapper,
                redisProperties,
                distributedLockGatewayProvider.getIfAvailable(DistributedLockGateway::noOp)
        );
    }

    public TransferSessionStore(StringRedisTemplate stringRedisTemplate,
                                ObjectMapper objectMapper,
                                AppRedisProperties redisProperties,
                                DistributedLockGateway distributedLockGateway) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
        this.distributedLockGateway = distributedLockGateway == null ? DistributedLockGateway.noOp() : distributedLockGateway;
    }

    public void save(TransferSession session) {
        if (session == null) {
            return;
        }
        if (redisEnabled()) {
            Duration ttl = resolveTtl(session.toState().expiresAt());
            try {
                stringRedisTemplate.opsForValue().set(
                        buildSessionKey(session.sessionId()),
                        objectMapper.writeValueAsString(session.toState()),
                        ttl
                );
                stringRedisTemplate.opsForValue().set(
                        buildPickupCodeKey(session.pickupCode()),
                        session.sessionId(),
                        ttl
                );
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize transfer session {}", session.sessionId(), ex);
                throw new IllegalStateException("failed to serialize transfer session", ex);
            }
            return;
        }
        sessionsById.put(session.sessionId(), session);
        sessionIdsByPickupCode.put(session.pickupCode(), session.sessionId());
    }

    public Optional<TransferSession> findById(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        if (redisEnabled()) {
            String rawValue = stringRedisTemplate.opsForValue().get(buildSessionKey(sessionId));
            if (!StringUtils.hasText(rawValue)) {
                return Optional.empty();
            }
            try {
                TransferSession session = TransferSession.fromState(objectMapper.readValue(rawValue, TransferSessionState.class));
                if (session.isExpired(Instant.now())) {
                    remove(session);
                    return Optional.empty();
                }
                return Optional.of(session);
            } catch (JsonProcessingException ex) {
                stringRedisTemplate.delete(buildSessionKey(sessionId));
                return Optional.empty();
            }
        }

        TransferSession session = sessionsById.get(sessionId);
        if (session != null && session.isExpired(Instant.now())) {
            remove(session);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    public Optional<TransferSession> findByPickupCode(String pickupCode) {
        if (!StringUtils.hasText(pickupCode)) {
            return Optional.empty();
        }
        if (redisEnabled()) {
            String sessionId = stringRedisTemplate.opsForValue().get(buildPickupCodeKey(pickupCode));
            if (!StringUtils.hasText(sessionId) || RESERVED_PICKUP_CODE.equals(sessionId)) {
                return Optional.empty();
            }
            Optional<TransferSession> session = findById(sessionId);
            if (session.isEmpty()) {
                stringRedisTemplate.delete(buildPickupCodeKey(pickupCode));
            }
            return session;
        }

        String sessionId = sessionIdsByPickupCode.get(pickupCode);
        if (sessionId == null) {
            return Optional.empty();
        }

        return findById(sessionId);
    }

    public void remove(TransferSession session) {
        if (session == null) {
            return;
        }
        if (redisEnabled()) {
            stringRedisTemplate.delete(buildSessionKey(session.sessionId()));
            stringRedisTemplate.delete(buildPickupCodeKey(session.pickupCode()));
            return;
        }
        sessionsById.remove(session.sessionId(), session);
        sessionIdsByPickupCode.remove(session.pickupCode(), session.sessionId());
    }

    public void pruneExpired(Instant now) {
        if (redisEnabled()) {
            return;
        }
        for (TransferSession session : sessionsById.values()) {
            if (session.isExpired(now)) {
                remove(session);
            }
        }
    }

    public String nextPickupCode() {
        if (redisEnabled()) {
            Duration reservationTtl = Duration.ofSeconds(Math.max(
                    redisProperties == null ? 60L : redisProperties.getTtlBufferSeconds(),
                    60L
            ));
            String pickupCode;
            do {
                pickupCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
            } while (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                    buildPickupCodeKey(pickupCode),
                    RESERVED_PICKUP_CODE,
                    reservationTtl
            )));
            return pickupCode;
        }

        String pickupCode;
        do {
            pickupCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        } while (sessionIdsByPickupCode.containsKey(pickupCode));
        return pickupCode;
    }

    public <T> T executeWithSessionLock(String sessionId, Supplier<T> action) {
        if (!StringUtils.hasText(sessionId)) {
            return action.get();
        }
        return distributedLockGateway.executeWithLock("transfer-session:" + sessionId.trim(), SESSION_LOCK_TTL, action);
    }

    public <T> T withSession(String sessionId, Function<TransferSession, T> action) {
        return executeWithSessionLock(sessionId, () -> findById(sessionId)
                .map(action)
                .orElse(null));
    }

    private Duration resolveTtl(Instant expiresAt) {
        long bufferSeconds = redisProperties == null ? 60L : redisProperties.getTtlBufferSeconds();
        Duration base = Duration.ofSeconds(Math.max(bufferSeconds, 60L));
        if (expiresAt == null) {
            return base;
        }
        long seconds = Math.max(1L, Duration.between(Instant.now(), expiresAt).getSeconds());
        return Duration.ofSeconds(seconds + bufferSeconds);
    }

    private String buildSessionKey(String sessionId) {
        return buildPrefix() + ":session:" + sessionId.trim();
    }

    private String buildPickupCodeKey(String pickupCode) {
        return buildPrefix() + ":pickup:" + pickupCode.trim();
    }

    private String buildPrefix() {
        String keyPrefix = redisProperties == null ? "yoyuzh" : redisProperties.getKeyPrefix();
        String namespace = redisProperties == null
                ? "transfer-sessions"
                : redisProperties.getNamespaces().getTransferSessions();
        return keyPrefix + ":" + namespace;
    }

    private boolean redisEnabled() {
        return redisProperties != null && redisProperties.isEnabled() && stringRedisTemplate != null;
    }
}
