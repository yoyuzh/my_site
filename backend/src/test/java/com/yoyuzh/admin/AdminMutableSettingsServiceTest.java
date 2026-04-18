package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMutableSettingsServiceTest {

    @Mock
    private RegistrationInviteService registrationInviteService;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;
    @Mock
    private AdminConfigSnapshotService adminConfigSnapshotService;

    private AdminMutableSettingsService adminMutableSettingsService;

    @BeforeEach
    void setUp() {
        adminMutableSettingsService = new AdminMutableSettingsService(
                registrationInviteService,
                adminMetricsService,
                adminAuditService,
                adminRuntimeSettingsService,
                adminConfigSnapshotService
        );
    }

    @Test
    void shouldUpdateWholeAdminSettingsSnapshot() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(new AdminRuntimeSettingsService.State(
                false,
                true,
                java.util.List.of("MODERATOR", "ADMIN"),
                900L,
                1209600L,
                false,
                60L,
                true,
                false,
                false,
                "in-memory",
                3000L,
                15000L,
                false,
                "local",
                false
        ));
        when(adminMetricsService.getOfflineTransferStorageLimitBytes()).thenReturn(1024L);
        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                new AdminSettingsUpdateRequest.SiteSection(true),
                new AdminSettingsUpdateRequest.RegistrationSection(
                        false,
                        "INV-CUSTOM-2026",
                        java.util.List.of("ADMIN")
                ),
                new AdminSettingsUpdateRequest.UserSessionSection(1200L, 2400L, true, 45L),
                new AdminSettingsUpdateRequest.TransferSection(2048L),
                new AdminSettingsUpdateRequest.MediaProcessingSection(true, true, false),
                new AdminSettingsUpdateRequest.QueueSection("redis", 1000L, 3000L),
                new AdminSettingsUpdateRequest.AppearanceSection(true),
                new AdminSettingsUpdateRequest.ServerSection("s3", true)
        );
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-CURRENT");
        AdminSettingsResponse expected = new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(true, true),
                new AdminSettingsResponse.RegistrationSection(false, "INV-CURRENT", java.util.List.of("ADMIN"), true),
                new AdminSettingsResponse.UserSessionSection(1200L, 2400L, true, 45L, true),
                new AdminSettingsResponse.TransferSection(2048L, true),
                new AdminSettingsResponse.MediaProcessingSection(true, true, false, true),
                new AdminSettingsResponse.QueueSection("redis", 1000L, 3000L, true),
                new AdminSettingsResponse.AppearanceSection(true, true),
                new AdminSettingsResponse.ServerSection("s3", true, true)
        );
        when(adminConfigSnapshotService.getSettings()).thenReturn(expected);

        AdminSettingsResponse response = adminMutableSettingsService.updateSettings(request);

        assertThat(response).isSameAs(expected);
        verify(registrationInviteService).getCurrentInviteCode();
        verify(registrationInviteService, never()).updateCurrentInviteCode(anyString());
        verify(adminRuntimeSettingsService).update(argThat(effective ->
                !effective.site().supported()
                        && !effective.registration().inviteCodeRequired()
                        && effective.registration().currentInviteCode().equals("INV-CURRENT")
                        && effective.registration().managementRoles().equals(java.util.List.of("ADMIN"))
                        && effective.userSession().accessExpirationSeconds() == 900L
                        && effective.userSession().refreshExpirationSeconds() == 1209600L
                        && !effective.userSession().tokenBlacklistEnabled()
                        && effective.userSession().tokenBlacklistTtlBufferSeconds() == 60L
                        && effective.transfer().offlineTransferStorageLimitBytes() == 2048L
                        && effective.queue().backend().equals("in-memory")
                        && effective.queue().mediaMetadataFixedDelayMs() == 3000L
                        && effective.queue().mediaMetadataInitialDelayMs() == 15000L
                        && !effective.appearance().supported()
                        && effective.server().storageProvider().equals("local")
                        && !effective.server().redisEnabled()
                ));
        verify(adminMetricsService).updateOfflineTransferStorageLimit(2048L);
    }

    @Test
    void shouldAllowRegistrationOnlyUpdateWithoutTransferPayload() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(new AdminRuntimeSettingsService.State(
                false,
                true,
                java.util.List.of("MODERATOR", "ADMIN"),
                900L,
                1209600L,
                false,
                60L,
                true,
                false,
                false,
                "in-memory",
                3000L,
                15000L,
                false,
                "local",
                false
        ));
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-OLD");
        when(adminMetricsService.getOfflineTransferStorageLimitBytes()).thenReturn(1024L);
        when(adminConfigSnapshotService.getSettings()).thenReturn(new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(false, false),
                new AdminSettingsResponse.RegistrationSection(false, "INV-OLD", java.util.List.of("ADMIN"), true),
                new AdminSettingsResponse.UserSessionSection(900L, 1209600L, false, 60L, false),
                new AdminSettingsResponse.TransferSection(1024L, true),
                new AdminSettingsResponse.MediaProcessingSection(true, false, false, false),
                new AdminSettingsResponse.QueueSection("in-memory", 3000L, 15000L, false),
                new AdminSettingsResponse.AppearanceSection(false, false),
                new AdminSettingsResponse.ServerSection("local", false, false)
        ));

        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                null,
                new AdminSettingsUpdateRequest.RegistrationSection(
                        false,
                        "INV-NEW",
                        java.util.List.of("ADMIN")
                ),
                null,
                null,
                null,
                null,
                null,
                null
        );

        adminMutableSettingsService.updateSettings(request);

        verify(adminRuntimeSettingsService).update(argThat(effective ->
                !effective.registration().inviteCodeRequired()
                        && effective.registration().currentInviteCode().equals("INV-OLD")
                        && effective.registration().managementRoles().equals(java.util.List.of("ADMIN"))
                        && effective.transfer().offlineTransferStorageLimitBytes() == 1024L
        ));
        verify(adminMetricsService, never()).updateOfflineTransferStorageLimit(anyLong());
        verify(registrationInviteService, never()).updateCurrentInviteCode(anyString());
    }

    @Test
    void shouldPreserveCurrentInviteCodeWhenUpdatingRegistrationSettings() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(new AdminRuntimeSettingsService.State(
                false,
                true,
                java.util.List.of("MODERATOR", "ADMIN"),
                900L,
                1209600L,
                false,
                60L,
                true,
                false,
                false,
                "in-memory",
                3000L,
                15000L,
                false,
                "local",
                false
        ));
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-LATEST");
        when(adminMetricsService.getOfflineTransferStorageLimitBytes()).thenReturn(1024L);
        when(adminConfigSnapshotService.getSettings()).thenReturn(new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(false, false),
                new AdminSettingsResponse.RegistrationSection(false, "INV-LATEST", java.util.List.of("ADMIN"), true),
                new AdminSettingsResponse.UserSessionSection(900L, 1209600L, false, 60L, false),
                new AdminSettingsResponse.TransferSection(1024L, true),
                new AdminSettingsResponse.MediaProcessingSection(true, false, false, false),
                new AdminSettingsResponse.QueueSection("in-memory", 3000L, 15000L, false),
                new AdminSettingsResponse.AppearanceSection(false, false),
                new AdminSettingsResponse.ServerSection("local", false, false)
        ));

        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                null,
                new AdminSettingsUpdateRequest.RegistrationSection(
                        false,
                        "INV-STALE",
                        java.util.List.of("ADMIN")
                ),
                null,
                null,
                null,
                null,
                null,
                null
        );

        AdminSettingsResponse response = adminMutableSettingsService.updateSettings(request);

        assertThat(response.registration().currentInviteCode()).isEqualTo("INV-LATEST");
        verify(adminRuntimeSettingsService).update(argThat(effective ->
                effective.registration().currentInviteCode().equals("INV-LATEST")
                        && effective.registration().managementRoles().equals(java.util.List.of("ADMIN"))
        ));
        verify(registrationInviteService, never()).updateCurrentInviteCode(anyString());
    }

    @Test
    void shouldRejectSettingsUpdateWithoutWritableSections() {
        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                new AdminSettingsUpdateRequest.SiteSection(true),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> adminMutableSettingsService.updateSettings(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one writable section is required");
    }

    @Test
    void shouldUpdateCurrentInviteCodeForAdminSettings() {
        when(registrationInviteService.updateCurrentInviteCode("INV-NEXT-2026")).thenReturn("INV-NEXT-2026");

        AdminRegistrationInviteCodeResponse response = adminMutableSettingsService.updateRegistrationInviteCode("  INV-NEXT-2026  ");

        assertThat(response.currentInviteCode()).isEqualTo("INV-NEXT-2026");
        verify(registrationInviteService).updateCurrentInviteCode("INV-NEXT-2026");
    }

    @Test
    void shouldRotateCurrentInviteCodeForAdminSettings() {
        when(registrationInviteService.rotateCurrentInviteCode()).thenReturn("INV-ROTATED-2026");

        AdminRegistrationInviteCodeResponse response = adminMutableSettingsService.rotateRegistrationInviteCode();

        assertThat(response.currentInviteCode()).isEqualTo("INV-ROTATED-2026");
        verify(registrationInviteService).rotateCurrentInviteCode();
    }

    @Test
    void shouldUpdateOfflineTransferStorageLimit() {
        AdminOfflineTransferStorageLimitResponse expected = new AdminOfflineTransferStorageLimitResponse(1024L);
        when(adminMetricsService.updateOfflineTransferStorageLimit(1024L)).thenReturn(expected);

        AdminOfflineTransferStorageLimitResponse response = adminMutableSettingsService.updateOfflineTransferStorageLimit(1024L);

        assertThat(response).isSameAs(expected);
        verify(adminMetricsService).updateOfflineTransferStorageLimit(1024L);
    }
}
