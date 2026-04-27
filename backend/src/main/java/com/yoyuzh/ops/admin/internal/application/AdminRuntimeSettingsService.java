package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.api.AdminRuntimeSettingsApi;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsState;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsStateRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
                                       AdminRuntimeSettingsDefaults adminRuntimeSettingsDefaults) {
        this.adminRuntimeSettingsStateRepository = adminRuntimeSettingsStateRepository;
        defaultState = adminRuntimeSettingsDefaults.create();
    }

    @Transactional(readOnly = true)
    public State snapshot() {
        return toState(ensureCurrentState());
    }

    @Transactional
    public State update(AdminSettingsUpdateRequest request) {
        AdminRuntimeSettingsState state = ensureCurrentStateForUpdate();
        State currentState = toState(state);
        State next = mergeState(currentState, request);
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
                state.isSiteSupported() == null ? defaultState.siteSupported() : state.isSiteSupported(),
                state.isRegistrationInviteCodeRequired() == null
                        ? defaultState.registrationInviteCodeRequired()
                        : state.isRegistrationInviteCodeRequired(),
                parseManagementRoles(state.getRegistrationManagementRoles()),
                normalizePositiveLong(
                        state.getUserSessionAccessExpirationSeconds(),
                        defaultState.userSessionAccessExpirationSeconds()
                ),
                normalizePositiveLong(
                        state.getUserSessionRefreshExpirationSeconds(),
                        defaultState.userSessionRefreshExpirationSeconds()
                ),
                state.isUserSessionTokenBlacklistEnabled() == null
                        ? defaultState.userSessionTokenBlacklistEnabled()
                        : state.isUserSessionTokenBlacklistEnabled(),
                normalizeNonNegativeLong(
                        state.getUserSessionTokenBlacklistTtlBufferSeconds(),
                        defaultState.userSessionTokenBlacklistTtlBufferSeconds()
                ),
                state.isMediaMetadataExtractionEnabled() == null
                        ? defaultState.mediaMetadataExtractionEnabled()
                        : state.isMediaMetadataExtractionEnabled(),
                state.isMediaThumbnailGenerationEnabled() == null
                        ? defaultState.mediaThumbnailGenerationEnabled()
                        : state.isMediaThumbnailGenerationEnabled(),
                state.isMediaVideoPosterEnabled() == null
                        ? defaultState.mediaVideoPosterEnabled()
                        : state.isMediaVideoPosterEnabled(),
                normalizeQueueBackend(state.getQueueBackend(), defaultState.queueBackend()),
                normalizePositiveLong(
                        state.getQueueMediaMetadataFixedDelayMs(),
                        defaultState.queueMediaMetadataFixedDelayMs()
                ),
                normalizePositiveLong(
                        state.getQueueMediaMetadataInitialDelayMs(),
                        defaultState.queueMediaMetadataInitialDelayMs()
                ),
                state.isAppearanceSupported() == null
                        ? defaultState.appearanceSupported()
                        : state.isAppearanceSupported(),
                normalizeStorageProvider(state.getServerStorageProvider(), defaultState.serverStorageProvider()),
                state.isServerRedisEnabled() == null
                        ? defaultState.serverRedisEnabled()
                        : state.isServerRedisEnabled()
        );
    }

    private State mergeState(State currentState, AdminSettingsUpdateRequest request) {
        if (request == null) {
            return currentState;
        }
        return new State(
                request.site() == null ? currentState.siteSupported() : request.site().supported(),
                request.registration() == null
                        ? currentState.registrationInviteCodeRequired()
                        : request.registration().inviteCodeRequired(),
                request.registration() == null
                        ? currentState.registrationManagementRoles()
                        : normalizeManagementRoles(request.registration().managementRoles()),
                request.userSession() == null
                        ? currentState.userSessionAccessExpirationSeconds()
                        : request.userSession().accessExpirationSeconds(),
                request.userSession() == null
                        ? currentState.userSessionRefreshExpirationSeconds()
                        : request.userSession().refreshExpirationSeconds(),
                request.userSession() == null
                        ? currentState.userSessionTokenBlacklistEnabled()
                        : request.userSession().tokenBlacklistEnabled(),
                request.userSession() == null
                        ? currentState.userSessionTokenBlacklistTtlBufferSeconds()
                        : request.userSession().tokenBlacklistTtlBufferSeconds(),
                request.mediaProcessing() == null
                        ? currentState.mediaMetadataExtractionEnabled()
                        : request.mediaProcessing().metadataExtractionEnabled(),
                request.mediaProcessing() == null
                        ? currentState.mediaThumbnailGenerationEnabled()
                        : request.mediaProcessing().thumbnailGenerationEnabled(),
                request.mediaProcessing() == null
                        ? currentState.mediaVideoPosterEnabled()
                        : request.mediaProcessing().videoPosterEnabled(),
                request.queue() == null ? currentState.queueBackend() : request.queue().backend(),
                request.queue() == null
                        ? currentState.queueMediaMetadataFixedDelayMs()
                        : request.queue().mediaMetadataFixedDelayMs(),
                request.queue() == null
                        ? currentState.queueMediaMetadataInitialDelayMs()
                        : request.queue().mediaMetadataInitialDelayMs(),
                request.appearance() == null ? currentState.appearanceSupported() : request.appearance().supported(),
                request.server() == null ? currentState.serverStorageProvider() : request.server().storageProvider(),
                request.server() == null ? currentState.serverRedisEnabled() : request.server().redisEnabled()
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
        target.setQueueBackend(normalizeQueueBackend(state.queueBackend(), defaultState.queueBackend()));
        target.setQueueMediaMetadataFixedDelayMs(state.queueMediaMetadataFixedDelayMs());
        target.setQueueMediaMetadataInitialDelayMs(state.queueMediaMetadataInitialDelayMs());
        target.setAppearanceSupported(state.appearanceSupported());
        target.setServerStorageProvider(normalizeStorageProvider(state.serverStorageProvider(), defaultState.serverStorageProvider()));
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

    private static String normalizeQueueBackend(String backend, String fallback) {
        String normalized = backend == null ? "" : backend.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return StringUtils.hasText(fallback) ? fallback : "in-memory";
    }

    private static String normalizeStorageProvider(String provider, String fallback) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return StringUtils.hasText(fallback) ? fallback : "local";
    }

    private static long normalizePositiveLong(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static long normalizeNonNegativeLong(Long value, long fallback) {
        return value == null || value < 0 ? fallback : value;
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
