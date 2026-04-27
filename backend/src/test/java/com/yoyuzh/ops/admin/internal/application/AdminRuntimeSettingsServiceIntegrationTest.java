package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsState;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:admin_runtime_settings_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-admin-runtime-settings"
        }
)
class AdminRuntimeSettingsServiceIntegrationTest {

    @Autowired
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    @Autowired
    private AdminRuntimeSettingsStateRepository adminRuntimeSettingsStateRepository;

    @BeforeEach
    void setUp() {
        adminRuntimeSettingsStateRepository.deleteAll();
    }

    @Test
    void shouldPersistUpdatedSettingsInDatabase() {
        adminRuntimeSettingsService.update(buildUpdateRequest(
                true,
                false,
                List.of("ADMIN"),
                1200L,
                86400L,
                true,
                45L,
                true,
                true,
                true,
                "redis",
                1000L,
                5000L,
                true,
                "s3",
                true
        ));

        AdminRuntimeSettingsState persisted = adminRuntimeSettingsStateRepository.findById(1L).orElseThrow();
        assertThat(persisted.isSiteSupported()).isTrue();
        assertThat(persisted.isRegistrationInviteCodeRequired()).isFalse();
        assertThat(persisted.getRegistrationManagementRoles()).isEqualTo("ADMIN");
        assertThat(persisted.getUserSessionAccessExpirationSeconds()).isEqualTo(1200L);
        assertThat(persisted.getUserSessionRefreshExpirationSeconds()).isEqualTo(86400L);
        assertThat(persisted.isUserSessionTokenBlacklistEnabled()).isTrue();
        assertThat(persisted.getUserSessionTokenBlacklistTtlBufferSeconds()).isEqualTo(45L);
        assertThat(persisted.isMediaMetadataExtractionEnabled()).isTrue();
        assertThat(persisted.isMediaThumbnailGenerationEnabled()).isTrue();
        assertThat(persisted.isMediaVideoPosterEnabled()).isTrue();
        assertThat(persisted.getQueueBackend()).isEqualTo("redis");
        assertThat(persisted.getQueueMediaMetadataFixedDelayMs()).isEqualTo(1000L);
        assertThat(persisted.getQueueMediaMetadataInitialDelayMs()).isEqualTo(5000L);
        assertThat(persisted.isAppearanceSupported()).isTrue();
        assertThat(persisted.getServerStorageProvider()).isEqualTo("s3");
        assertThat(persisted.isServerRedisEnabled()).isTrue();

        AdminRuntimeSettingsService.State snapshot = adminRuntimeSettingsService.snapshot();
        assertThat(snapshot.siteSupported()).isTrue();
        assertThat(snapshot.registrationInviteCodeRequired()).isFalse();
        assertThat(snapshot.registrationManagementRoles()).containsExactly("ADMIN");
        assertThat(snapshot.userSessionAccessExpirationSeconds()).isEqualTo(1200L);
        assertThat(snapshot.userSessionRefreshExpirationSeconds()).isEqualTo(86400L);
        assertThat(snapshot.userSessionTokenBlacklistEnabled()).isTrue();
        assertThat(snapshot.userSessionTokenBlacklistTtlBufferSeconds()).isEqualTo(45L);
        assertThat(snapshot.mediaMetadataExtractionEnabled()).isTrue();
        assertThat(snapshot.mediaThumbnailGenerationEnabled()).isTrue();
        assertThat(snapshot.mediaVideoPosterEnabled()).isTrue();
        assertThat(snapshot.queueBackend()).isEqualTo("redis");
        assertThat(snapshot.queueMediaMetadataFixedDelayMs()).isEqualTo(1000L);
        assertThat(snapshot.queueMediaMetadataInitialDelayMs()).isEqualTo(5000L);
        assertThat(snapshot.appearanceSupported()).isTrue();
        assertThat(snapshot.serverStorageProvider()).isEqualTo("s3");
        assertThat(snapshot.serverRedisEnabled()).isTrue();
    }

    @Test
    void shouldCanonicalizeRolePrefixedManagementRoles() {
        adminRuntimeSettingsService.update(buildUpdateRequest(
                true,
                false,
                List.of("ROLE_ADMIN", " moderator "),
                1200L,
                86400L,
                true,
                45L,
                true,
                true,
                true,
                "redis",
                1000L,
                5000L,
                true,
                "s3",
                true
        ));

        AdminRuntimeSettingsState persisted = adminRuntimeSettingsStateRepository.findById(1L).orElseThrow();
        assertThat(persisted.getRegistrationManagementRoles()).isEqualTo("ADMIN,MODERATOR");

        AdminRuntimeSettingsService.State snapshot = adminRuntimeSettingsService.snapshot();
        assertThat(snapshot.registrationManagementRoles()).containsExactly("ADMIN", "MODERATOR");
    }

    @Test
    void shouldReadLatestPersistedStateWithoutProcessLocalCache() {
        AdminRuntimeSettingsService.State defaultState = adminRuntimeSettingsService.snapshot();
        AdminRuntimeSettingsState state = adminRuntimeSettingsStateRepository.findById(1L).orElseThrow();
        state.setRegistrationInviteCodeRequired(false);
        state.setRegistrationManagementRoles("ADMIN");
        state.setQueueBackend("redis");
        adminRuntimeSettingsStateRepository.saveAndFlush(state);

        AdminRuntimeSettingsService.State snapshot = adminRuntimeSettingsService.snapshot();
        assertThat(snapshot.registrationInviteCodeRequired()).isFalse();
        assertThat(snapshot.registrationManagementRoles()).containsExactly("ADMIN");
        assertThat(snapshot.queueBackend()).isEqualTo("redis");
    }

    @Test
    void shouldFallbackToDefaultsForLegacyBlankOrZeroRuntimeSettingsValues() {
        AdminRuntimeSettingsService.State defaultState = adminRuntimeSettingsService.snapshot();
        AdminRuntimeSettingsState state = adminRuntimeSettingsStateRepository.findById(1L).orElseThrow();
        state.setRegistrationManagementRoles(" ");
        state.setUserSessionAccessExpirationSeconds(0L);
        state.setUserSessionRefreshExpirationSeconds(0L);
        state.setUserSessionTokenBlacklistTtlBufferSeconds(-1L);
        state.setQueueBackend(" ");
        state.setQueueMediaMetadataFixedDelayMs(0L);
        state.setQueueMediaMetadataInitialDelayMs(0L);
        state.setServerStorageProvider(" ");
        adminRuntimeSettingsStateRepository.saveAndFlush(state);

        AdminRuntimeSettingsService.State snapshot = adminRuntimeSettingsService.snapshot();
        assertThat(snapshot.registrationManagementRoles()).isEqualTo(defaultState.registrationManagementRoles());
        assertThat(snapshot.userSessionAccessExpirationSeconds()).isEqualTo(defaultState.userSessionAccessExpirationSeconds());
        assertThat(snapshot.userSessionRefreshExpirationSeconds()).isEqualTo(defaultState.userSessionRefreshExpirationSeconds());
        assertThat(snapshot.userSessionTokenBlacklistTtlBufferSeconds()).isEqualTo(defaultState.userSessionTokenBlacklistTtlBufferSeconds());
        assertThat(snapshot.queueBackend()).isEqualTo(defaultState.queueBackend());
        assertThat(snapshot.queueMediaMetadataFixedDelayMs()).isEqualTo(defaultState.queueMediaMetadataFixedDelayMs());
        assertThat(snapshot.queueMediaMetadataInitialDelayMs()).isEqualTo(defaultState.queueMediaMetadataInitialDelayMs());
        assertThat(snapshot.serverStorageProvider()).isEqualTo(defaultState.serverStorageProvider());
    }

    @Test
    void shouldPreserveExistingNonWritableStateWhenApplyingPartialUpdate() {
        adminRuntimeSettingsService.update(buildUpdateRequest(
                true,
                false,
                List.of("ADMIN"),
                1200L,
                86400L,
                true,
                45L,
                true,
                true,
                true,
                "redis",
                1000L,
                5000L,
                true,
                "s3",
                true
        ));

        adminRuntimeSettingsService.update(new AdminSettingsUpdateRequest(
                null,
                new AdminSettingsUpdateRequest.RegistrationSection(true, "INV-PARTIAL-2026", List.of("MODERATOR", "ADMIN")),
                null,
                null,
                null,
                null,
                null,
                null
        ));

        AdminRuntimeSettingsService.State snapshot = adminRuntimeSettingsService.snapshot();
        assertThat(snapshot.registrationInviteCodeRequired()).isTrue();
        assertThat(snapshot.registrationManagementRoles()).containsExactly("MODERATOR", "ADMIN");
        assertThat(snapshot.siteSupported()).isTrue();
        assertThat(snapshot.userSessionAccessExpirationSeconds()).isEqualTo(1200L);
        assertThat(snapshot.queueBackend()).isEqualTo("redis");
        assertThat(snapshot.appearanceSupported()).isTrue();
        assertThat(snapshot.serverStorageProvider()).isEqualTo("s3");
    }

    @Test
    void shouldResetStateToDefaultPersistently() {
        AdminRuntimeSettingsService.State defaultState = adminRuntimeSettingsService.snapshot();

        adminRuntimeSettingsService.update(buildUpdateRequest(
                true,
                false,
                List.of("ADMIN"),
                1200L,
                86400L,
                true,
                45L,
                true,
                true,
                true,
                "redis",
                1000L,
                5000L,
                true,
                "s3",
                true
        ));

        adminRuntimeSettingsService.reset();
        AdminRuntimeSettingsService.State resetState = adminRuntimeSettingsService.snapshot();
        assertThat(resetState).isEqualTo(defaultState);
    }

    private static AdminSettingsUpdateRequest buildUpdateRequest(
            boolean siteSupported,
            boolean inviteCodeRequired,
            List<String> managementRoles,
            long accessExpirationSeconds,
            long refreshExpirationSeconds,
            boolean tokenBlacklistEnabled,
            long tokenBlacklistTtlBufferSeconds,
            boolean metadataExtractionEnabled,
            boolean thumbnailGenerationEnabled,
            boolean videoPosterEnabled,
            String queueBackend,
            long queueMediaMetadataFixedDelayMs,
            long queueMediaMetadataInitialDelayMs,
            boolean appearanceSupported,
            String storageProvider,
            boolean redisEnabled
    ) {
        return new AdminSettingsUpdateRequest(
                new AdminSettingsUpdateRequest.SiteSection(siteSupported),
                new AdminSettingsUpdateRequest.RegistrationSection(
                        inviteCodeRequired,
                        "INV-SETTINGS-2026",
                        managementRoles
                ),
                new AdminSettingsUpdateRequest.UserSessionSection(
                        accessExpirationSeconds,
                        refreshExpirationSeconds,
                        tokenBlacklistEnabled,
                        tokenBlacklistTtlBufferSeconds
                ),
                new AdminSettingsUpdateRequest.TransferSection(1024L),
                new AdminSettingsUpdateRequest.MediaProcessingSection(
                        metadataExtractionEnabled,
                        thumbnailGenerationEnabled,
                        videoPosterEnabled
                ),
                new AdminSettingsUpdateRequest.QueueSection(
                        queueBackend,
                        queueMediaMetadataFixedDelayMs,
                        queueMediaMetadataInitialDelayMs
                ),
                new AdminSettingsUpdateRequest.AppearanceSection(appearanceSupported),
                new AdminSettingsUpdateRequest.ServerSection(storageProvider, redisEnabled)
        );
    }
}
