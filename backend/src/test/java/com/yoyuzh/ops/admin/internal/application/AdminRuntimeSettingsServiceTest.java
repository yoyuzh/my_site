package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsStateRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRuntimeSettingsServiceTest {

    @Test
    void snapshotShouldReturnDefaultsWithoutPersistingWhenStateMissing() {
        AdminRuntimeSettingsStateRepository repository = mock(AdminRuntimeSettingsStateRepository.class);
        AdminRuntimeSettingsDefaults defaults = mock(AdminRuntimeSettingsDefaults.class);
        AdminRuntimeSettingsService.State defaultState = new AdminRuntimeSettingsService.State(
                false,
                true,
                List.of("MODERATOR", "ADMIN"),
                900L,
                1209600L,
                false,
                60L,
                true,
                false,
                false,
                "in-memory",
                30000L,
                30000L,
                false,
                "s3",
                false
        );

        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(defaults.create()).thenReturn(defaultState);
        AdminRuntimeSettingsState persistedState = new AdminRuntimeSettingsState();
        persistedState.setId(1L);
        persistedState.setSiteSupported(defaultState.siteSupported());
        persistedState.setRegistrationInviteCodeRequired(defaultState.registrationInviteCodeRequired());
        persistedState.setRegistrationManagementRoles(String.join(",", defaultState.registrationManagementRoles()));
        persistedState.setUserSessionAccessExpirationSeconds(defaultState.userSessionAccessExpirationSeconds());
        persistedState.setUserSessionRefreshExpirationSeconds(defaultState.userSessionRefreshExpirationSeconds());
        persistedState.setUserSessionTokenBlacklistEnabled(defaultState.userSessionTokenBlacklistEnabled());
        persistedState.setUserSessionTokenBlacklistTtlBufferSeconds(defaultState.userSessionTokenBlacklistTtlBufferSeconds());
        persistedState.setMediaMetadataExtractionEnabled(defaultState.mediaMetadataExtractionEnabled());
        persistedState.setMediaThumbnailGenerationEnabled(defaultState.mediaThumbnailGenerationEnabled());
        persistedState.setMediaVideoPosterEnabled(defaultState.mediaVideoPosterEnabled());
        persistedState.setQueueBackend(defaultState.queueBackend());
        persistedState.setQueueMediaMetadataFixedDelayMs(defaultState.queueMediaMetadataFixedDelayMs());
        persistedState.setQueueMediaMetadataInitialDelayMs(defaultState.queueMediaMetadataInitialDelayMs());
        persistedState.setAppearanceSupported(defaultState.appearanceSupported());
        persistedState.setServerStorageProvider(defaultState.serverStorageProvider());
        persistedState.setServerRedisEnabled(defaultState.serverRedisEnabled());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(AdminRuntimeSettingsState.class)))
                .thenReturn(persistedState);

        AdminRuntimeSettingsService service = new AdminRuntimeSettingsService(repository, defaults);

        assertThat(service.snapshot()).isEqualTo(defaultState);
        verify(repository).findById(1L);
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
