package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(registrationInviteService.updateCurrentInviteCode("INV-CUSTOM-2026")).thenReturn("INV-CUSTOM-2026");
        AdminSettingsResponse expected = new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(true, true),
                new AdminSettingsResponse.RegistrationSection(false, "INV-CUSTOM-2026", java.util.List.of("ADMIN"), true),
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
        verify(registrationInviteService).updateCurrentInviteCode("INV-CUSTOM-2026");
        verify(adminRuntimeSettingsService).update(org.mockito.ArgumentMatchers.any(AdminSettingsUpdateRequest.class));
        verify(adminMetricsService).updateOfflineTransferStorageLimit(2048L);
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
