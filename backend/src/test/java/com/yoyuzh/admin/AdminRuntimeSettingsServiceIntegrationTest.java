package com.yoyuzh.admin;

import com.yoyuzh.PortalBackendApplication;
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
        assertThat(persisted.isRegistrationInviteCodeRequired()).isFalse();
        assertThat(persisted.getRegistrationManagementRoles()).isEqualTo("ADMIN");
        assertThat(persisted.getQueueBackend()).isEqualTo("in-memory");

        AdminRuntimeSettingsService.State snapshot = adminRuntimeSettingsService.snapshot();
        assertThat(snapshot.registrationInviteCodeRequired()).isFalse();
        assertThat(snapshot.registrationManagementRoles()).containsExactly("ADMIN");
        assertThat(snapshot.queueBackend()).isEqualTo("in-memory");
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
        assertThat(snapshot.queueBackend()).isEqualTo(defaultState.queueBackend());
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
