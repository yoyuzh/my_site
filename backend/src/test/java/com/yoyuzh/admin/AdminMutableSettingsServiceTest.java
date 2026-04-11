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

    private AdminMutableSettingsService adminMutableSettingsService;

    @BeforeEach
    void setUp() {
        adminMutableSettingsService = new AdminMutableSettingsService(
                registrationInviteService,
                adminMetricsService,
                adminAuditService
        );
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
