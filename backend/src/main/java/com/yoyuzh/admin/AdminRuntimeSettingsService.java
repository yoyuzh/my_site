package com.yoyuzh.admin;

import com.yoyuzh.config.AppRedisProperties;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.config.JwtProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AdminRuntimeSettingsService {

    private static final List<String> DEFAULT_MANAGEMENT_ROLES = List.of("MODERATOR", "ADMIN");

    private final State defaultState;
    private final AtomicReference<State> stateRef;

    public AdminRuntimeSettingsService(AppRedisProperties redisProperties,
                                       FileStorageProperties fileStorageProperties,
                                       JwtProperties jwtProperties,
                                       Environment environment) {
        boolean redisEnabled = redisProperties.isEnabled();
        defaultState = new State(
                false,
                true,
                DEFAULT_MANAGEMENT_ROLES,
                jwtProperties.getAccessExpirationSeconds(),
                jwtProperties.getRefreshExpirationSeconds(),
                redisEnabled,
                redisProperties.getTtlBufferSeconds(),
                true,
                false,
                false,
                redisEnabled ? "redis" : "in-memory",
                environment.getProperty("app.redis.broker.media-meta.fixed-delay-ms", Long.class, 3000L),
                environment.getProperty("app.redis.broker.media-meta.initial-delay-ms", Long.class, 15000L),
                false,
                normalizeStorageProvider(fileStorageProperties.getProvider()),
                redisEnabled
        );
        stateRef = new AtomicReference<>(defaultState);
    }

    public State snapshot() {
        return stateRef.get();
    }

    public State update(AdminSettingsUpdateRequest request) {
        State next = new State(
                request.site().supported(),
                request.registration().inviteCodeRequired(),
                normalizeManagementRoles(request.registration().managementRoles()),
                request.userSession().accessExpirationSeconds(),
                request.userSession().refreshExpirationSeconds(),
                request.userSession().tokenBlacklistEnabled(),
                request.userSession().tokenBlacklistTtlBufferSeconds(),
                request.mediaProcessing().metadataExtractionEnabled(),
                request.mediaProcessing().thumbnailGenerationEnabled(),
                request.mediaProcessing().videoPosterEnabled(),
                normalizeQueueBackend(request.queue().backend()),
                request.queue().mediaMetadataFixedDelayMs(),
                request.queue().mediaMetadataInitialDelayMs(),
                request.appearance().supported(),
                normalizeStorageProvider(request.server().storageProvider()),
                request.server().redisEnabled()
        );
        stateRef.set(next);
        return next;
    }

    public boolean isInviteCodeRequired() {
        return stateRef.get().registrationInviteCodeRequired();
    }

    public void reset() {
        stateRef.set(defaultState);
    }

    private static String normalizeQueueBackend(String backend) {
        String normalized = backend == null ? "" : backend.trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : "in-memory";
    }

    private static String normalizeStorageProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : "local";
    }

    private static List<String> normalizeManagementRoles(List<String> roles) {
        if (roles == null) {
            return DEFAULT_MANAGEMENT_ROLES;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            String trimmed = role.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed.toUpperCase(Locale.ROOT));
            }
        }
        if (normalized.isEmpty()) {
            return DEFAULT_MANAGEMENT_ROLES;
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    public record State(
            boolean siteSupported,
            boolean registrationInviteCodeRequired,
            List<String> registrationManagementRoles,
            long userSessionAccessExpirationSeconds,
            long userSessionRefreshExpirationSeconds,
            boolean userSessionTokenBlacklistEnabled,
            long userSessionTokenBlacklistTtlBufferSeconds,
            boolean mediaMetadataExtractionEnabled,
            boolean mediaThumbnailGenerationEnabled,
            boolean mediaVideoPosterEnabled,
            String queueBackend,
            long queueMediaMetadataFixedDelayMs,
            long queueMediaMetadataInitialDelayMs,
            boolean appearanceSupported,
            String serverStorageProvider,
            boolean serverRedisEnabled
    ) {
    }
}
