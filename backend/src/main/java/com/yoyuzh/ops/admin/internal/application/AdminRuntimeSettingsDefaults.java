package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.IdentitySessionRuntimeSettingsApi;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminRuntimeSettingsDefaults {

    private static final List<String> DEFAULT_MANAGEMENT_ROLES = List.of("MODERATOR", "ADMIN");

    private final AppRedisProperties redisProperties;
    private final StorageRuntimeProperties storageRuntimeProperties;
    private final IdentitySessionRuntimeSettingsApi identitySessionRuntimeSettingsApi;
    private final Environment environment;

    public AdminRuntimeSettingsService.State create() {
        boolean redisEnabled = redisProperties.isEnabled();
        return new AdminRuntimeSettingsService.State(
                false,
                true,
                DEFAULT_MANAGEMENT_ROLES,
                identitySessionRuntimeSettingsApi.accessExpirationSeconds(),
                identitySessionRuntimeSettingsApi.refreshExpirationSeconds(),
                redisEnabled,
                redisProperties.getTtlBufferSeconds(),
                true,
                false,
                false,
                redisEnabled ? "redis" : "in-memory",
                environment.getProperty("app.redis.broker.media-meta.fixed-delay-ms", Long.class, 3000L),
                environment.getProperty("app.redis.broker.media-meta.initial-delay-ms", Long.class, 15000L),
                false,
                normalizeStorageProvider(storageRuntimeProperties.getProvider()),
                redisEnabled
        );
    }

    private String normalizeStorageProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        return org.springframework.util.StringUtils.hasText(normalized) ? normalized : "local";
    }
}
