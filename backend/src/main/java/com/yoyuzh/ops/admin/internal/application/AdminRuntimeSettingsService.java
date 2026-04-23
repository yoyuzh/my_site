package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.ops.admin.api.AdminRuntimeSettingsApi;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsState;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsStateRepository;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.boot.security.JwtProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminRuntimeSettingsService implements AdminRuntimeSettingsApi {

    private static final Long STATE_ID = 1L;
    private static final List<String> DEFAULT_MANAGEMENT_ROLES = List.of("MODERATOR", "ADMIN");
    private static final String MANAGEMENT_ROLE_DELIMITER = ",";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AdminRuntimeSettingsStateRepository adminRuntimeSettingsStateRepository;
    private final State defaultState;

    public AdminRuntimeSettingsService(AdminRuntimeSettingsStateRepository adminRuntimeSettingsStateRepository,
                                       AppRedisProperties redisProperties,
                                       StorageRuntimeProperties storageRuntimeProperties,
                                       JwtProperties jwtProperties,
                                       Environment environment) {
        this.adminRuntimeSettingsStateRepository = adminRuntimeSettingsStateRepository;
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
                normalizeStorageProvider(storageRuntimeProperties.getProvider()),
                redisEnabled
        );
    }

    @Transactional(readOnly = true)
    public State snapshot() {
        return toState(ensureCurrentState());
    }

    @Transactional
    public State update(AdminSettingsUpdateRequest request) {
        if (request.registration() == null) {
            throw new IllegalArgumentException("registration section is required");
        }
        AdminRuntimeSettingsState state = ensureCurrentStateForUpdate();
        State next = new State(
                defaultState.siteSupported(),
                request.registration().inviteCodeRequired(),
                normalizeManagementRoles(request.registration().managementRoles()),
                defaultState.userSessionAccessExpirationSeconds(),
                defaultState.userSessionRefreshExpirationSeconds(),
                defaultState.userSessionTokenBlacklistEnabled(),
                defaultState.userSessionTokenBlacklistTtlBufferSeconds(),
                defaultState.mediaMetadataExtractionEnabled(),
                defaultState.mediaThumbnailGenerationEnabled(),
                defaultState.mediaVideoPosterEnabled(),
                defaultState.queueBackend(),
                defaultState.queueMediaMetadataFixedDelayMs(),
                defaultState.queueMediaMetadataInitialDelayMs(),
                defaultState.appearanceSupported(),
                defaultState.serverStorageProvider(),
                defaultState.serverRedisEnabled()
        );
        applyState(state, next);
        return toState(adminRuntimeSettingsStateRepository.save(state));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isInviteCodeRequired() {
        return snapshot().registrationInviteCodeRequired();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> registrationManagementRoles() {
        return snapshot().registrationManagementRoles();
    }

    @Transactional
    public void reset() {
        AdminRuntimeSettingsState state = ensureCurrentStateForUpdate();
        applyState(state, defaultState);
        adminRuntimeSettingsStateRepository.save(state);
    }

    private AdminRuntimeSettingsState ensureCurrentState() {
        return adminRuntimeSettingsStateRepository.findById(STATE_ID)
                .orElseGet(this::createInitialState);
    }

    private AdminRuntimeSettingsState ensureCurrentStateForUpdate() {
        return adminRuntimeSettingsStateRepository.findByIdForUpdate(STATE_ID)
                .orElseGet(() -> {
                    createInitialState();
                    return adminRuntimeSettingsStateRepository.findByIdForUpdate(STATE_ID)
                            .orElseThrow(() -> new IllegalStateException("admin runtime settings state init failed"));
                });
    }

    private AdminRuntimeSettingsState createInitialState() {
        AdminRuntimeSettingsState state = new AdminRuntimeSettingsState();
        state.setId(STATE_ID);
        applyState(state, defaultState);
        try {
            return adminRuntimeSettingsStateRepository.saveAndFlush(state);
        } catch (DataIntegrityViolationException ignored) {
            return adminRuntimeSettingsStateRepository.findById(STATE_ID)
                    .orElseThrow(() -> ignored);
        }
    }

    private State toState(AdminRuntimeSettingsState state) {
        return new State(
                defaultState.siteSupported(),
                state.isRegistrationInviteCodeRequired(),
                parseManagementRoles(state.getRegistrationManagementRoles()),
                defaultState.userSessionAccessExpirationSeconds(),
                defaultState.userSessionRefreshExpirationSeconds(),
                defaultState.userSessionTokenBlacklistEnabled(),
                defaultState.userSessionTokenBlacklistTtlBufferSeconds(),
                defaultState.mediaMetadataExtractionEnabled(),
                defaultState.mediaThumbnailGenerationEnabled(),
                defaultState.mediaVideoPosterEnabled(),
                normalizeQueueBackend(defaultState.queueBackend()),
                defaultState.queueMediaMetadataFixedDelayMs(),
                defaultState.queueMediaMetadataInitialDelayMs(),
                defaultState.appearanceSupported(),
                normalizeStorageProvider(defaultState.serverStorageProvider()),
                defaultState.serverRedisEnabled()
        );
    }

    private void applyState(AdminRuntimeSettingsState target, State state) {
        target.setSiteSupported(state.siteSupported());
        target.setRegistrationInviteCodeRequired(state.registrationInviteCodeRequired());
        target.setRegistrationManagementRoles(serializeManagementRoles(state.registrationManagementRoles()));
        target.setUserSessionAccessExpirationSeconds(state.userSessionAccessExpirationSeconds());
        target.setUserSessionRefreshExpirationSeconds(state.userSessionRefreshExpirationSeconds());
        target.setUserSessionTokenBlacklistEnabled(state.userSessionTokenBlacklistEnabled());
        target.setUserSessionTokenBlacklistTtlBufferSeconds(state.userSessionTokenBlacklistTtlBufferSeconds());
        target.setMediaMetadataExtractionEnabled(state.mediaMetadataExtractionEnabled());
        target.setMediaThumbnailGenerationEnabled(state.mediaThumbnailGenerationEnabled());
        target.setMediaVideoPosterEnabled(state.mediaVideoPosterEnabled());
        target.setQueueBackend(normalizeQueueBackend(state.queueBackend()));
        target.setQueueMediaMetadataFixedDelayMs(state.queueMediaMetadataFixedDelayMs());
        target.setQueueMediaMetadataInitialDelayMs(state.queueMediaMetadataInitialDelayMs());
        target.setAppearanceSupported(state.appearanceSupported());
        target.setServerStorageProvider(normalizeStorageProvider(state.serverStorageProvider()));
        target.setServerRedisEnabled(state.serverRedisEnabled());
    }

    private static String serializeManagementRoles(List<String> roles) {
        List<String> normalizedRoles = normalizeManagementRoles(roles);
        return String.join(MANAGEMENT_ROLE_DELIMITER, normalizedRoles);
    }

    private static List<String> parseManagementRoles(String persistedRoles) {
        if (!StringUtils.hasText(persistedRoles)) {
            return DEFAULT_MANAGEMENT_ROLES;
        }
        return normalizeManagementRoles(Arrays.asList(persistedRoles.split(MANAGEMENT_ROLE_DELIMITER)));
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
            String normalizedRole = normalizeManagementRole(role);
            if (normalizedRole != null) {
                normalized.add(normalizedRole);
            }
        }
        if (normalized.isEmpty()) {
            return DEFAULT_MANAGEMENT_ROLES;
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    static String normalizeManagementRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith(ROLE_PREFIX)) {
            normalized = normalized.substring(ROLE_PREFIX.length()).trim();
        }
        return normalized.isEmpty() ? null : normalized;
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
