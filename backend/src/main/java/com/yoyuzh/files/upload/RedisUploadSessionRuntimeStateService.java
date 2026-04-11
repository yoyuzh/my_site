package com.yoyuzh.files.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.config.AppRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisUploadSessionRuntimeStateService implements UploadSessionRuntimeStateService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AppRedisProperties redisProperties;
    private final ObjectMapper objectMapper;

    public RedisUploadSessionRuntimeStateService(StringRedisTemplate stringRedisTemplate,
                                                 AppRedisProperties redisProperties,
                                                 ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<UploadSessionRuntimeState> getState(String sessionId) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(sessionId));
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, UploadSessionRuntimeState.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void markCreated(UploadSession session) {
        LocalDateTime updatedAt = safeUpdatedAt(session);
        writeState(session, new UploadSessionRuntimeState(
                "created",
                0L,
                0,
                0,
                updatedAt,
                session.getExpiresAt()
        ));
    }

    @Override
    public void markUploading(UploadSession session, long uploadedBytes, int uploadedPartCount, LocalDateTime updatedAt) {
        writeState(session, new UploadSessionRuntimeState(
                "uploading",
                Math.max(0L, uploadedBytes),
                Math.max(0, uploadedPartCount),
                toProgressPercent(uploadedBytes, session.getSize()),
                updatedAt,
                session.getExpiresAt()
        ));
    }

    @Override
    public void markCompleted(UploadSession session, LocalDateTime updatedAt) {
        writeState(session, new UploadSessionRuntimeState(
                "completed",
                session.getSize() == null ? 0L : session.getSize(),
                Math.max(1, session.getChunkCount() == null ? 1 : session.getChunkCount()),
                100,
                updatedAt,
                session.getExpiresAt()
        ));
    }

    @Override
    public void markCancelled(UploadSession session, LocalDateTime updatedAt) {
        rewritePhase(session, "cancelled", updatedAt);
    }

    @Override
    public void markFailed(UploadSession session, LocalDateTime updatedAt) {
        rewritePhase(session, "failed", updatedAt);
    }

    @Override
    public void markExpired(UploadSession session, LocalDateTime updatedAt) {
        rewritePhase(session, "expired", updatedAt);
    }

    private void rewritePhase(UploadSession session, String phase, LocalDateTime updatedAt) {
        UploadSessionRuntimeState current = getState(session.getSessionId()).orElse(new UploadSessionRuntimeState(
                phase,
                0L,
                0,
                0,
                updatedAt,
                session.getExpiresAt()
        ));
        writeState(session, new UploadSessionRuntimeState(
                phase,
                current.uploadedBytes(),
                current.uploadedPartCount(),
                current.progressPercent(),
                updatedAt,
                session.getExpiresAt()
        ));
    }

    private void writeState(UploadSession session, UploadSessionRuntimeState state) {
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    buildKey(session.getSessionId()),
                    objectMapper.writeValueAsString(state),
                    resolveTtl(session.getExpiresAt(), state.phase())
            );
        } catch (JsonProcessingException ignored) {
        }
    }

    private Duration resolveTtl(LocalDateTime expiresAt, String phase) {
        Duration base = Duration.ofSeconds(Math.max(redisProperties.getTtlBufferSeconds(), 60L));
        if (expiresAt == null) {
            return base;
        }
        long seconds = Math.max(1L, expiresAt.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC));
        Duration sessionWindow = Duration.ofSeconds(seconds + redisProperties.getTtlBufferSeconds());
        if ("completed".equals(phase) || "cancelled".equals(phase) || "failed".equals(phase) || "expired".equals(phase)) {
            return sessionWindow.compareTo(Duration.ofHours(1)) < 0 ? sessionWindow : Duration.ofHours(1);
        }
        return sessionWindow;
    }

    private Integer toProgressPercent(long uploadedBytes, Long totalBytes) {
        if (totalBytes == null || totalBytes <= 0) {
            return 0;
        }
        double ratio = Math.min(1.0d, Math.max(0.0d, (double) uploadedBytes / totalBytes));
        return (int) Math.round(ratio * 100);
    }

    private LocalDateTime safeUpdatedAt(UploadSession session) {
        return session.getUpdatedAt() == null ? LocalDateTime.now(ZoneOffset.UTC) : session.getUpdatedAt();
    }

    private String buildKey(String sessionId) {
        return redisProperties.getKeyPrefix()
                + ":" + redisProperties.getNamespaces().getUploadState()
                + ":session:" + sessionId.trim();
    }
}
