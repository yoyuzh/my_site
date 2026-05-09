package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.ops.admin.api.AdminRegistrationInviteCodeResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.shared.kernel.BusinessException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMutableSettingsServiceTest {

    @Mock
    private IdentityAdminSummaryApi identityAdminSummaryApi;
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
                identityAdminSummaryApi,
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
        when(identityAdminSummaryApi.currentInviteCode()).thenReturn("INV-CURRENT");
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
        verify(identityAdminSummaryApi).currentInviteCode();
        verify(identityAdminSummaryApi, never()).updateInviteCode(anyString());
        verify(adminRuntimeSettingsService).update(argThat(effective ->
                effective.site() == null
                        && !effective.registration().inviteCodeRequired()
                        && effective.registration().currentInviteCode().equals("INV-CURRENT")
                        && effective.registration().managementRoles().equals(java.util.List.of("ADMIN"))
                        && effective.userSession() == null
                        && effective.transfer().offlineTransferStorageLimitBytes() == 2048L
                        && effective.mediaProcessing() == null
                        && effective.queue() == null
                        && effective.appearance() == null
                        && effective.server() == null
                ));
        verify(adminMetricsService).updateOfflineTransferStorageLimit(2048L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.SETTINGS_UPDATED),
                eq("ADMIN_SETTINGS"),
                eq(null),
                eq("Updated admin settings"),
                argThat(details ->
                        Boolean.FALSE.equals(details.get("inviteCodeRequired"))
                                && Integer.valueOf(1).equals(details.get("managementRoleCount"))
                                && Long.valueOf(2048L).equals(details.get("offlineTransferStorageLimitBytes"))
                                && Boolean.TRUE.equals(details.get("registrationUpdated"))
                                && Boolean.TRUE.equals(details.get("transferUpdated")))
        );
    }

    @Test
    void shouldAllowRegistrationOnlyUpdateWithoutTransferPayload() {
        when(identityAdminSummaryApi.currentInviteCode()).thenReturn("INV-OLD");
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
                        && effective.transfer() == null
        ));
        verify(adminMetricsService, never()).updateOfflineTransferStorageLimit(anyLong());
        verify(identityAdminSummaryApi, never()).updateInviteCode(anyString());
    }

    @Test
    void shouldPreserveCurrentInviteCodeWhenUpdatingRegistrationSettings() {
        when(identityAdminSummaryApi.currentInviteCode()).thenReturn("INV-LATEST");
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
        verify(identityAdminSummaryApi, never()).updateInviteCode(anyString());
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
        when(identityAdminSummaryApi.updateInviteCode("INV-NEXT-2026")).thenReturn("INV-NEXT-2026");

        AdminRegistrationInviteCodeResponse response = adminMutableSettingsService.updateRegistrationInviteCode("  INV-NEXT-2026  ");

        assertThat(response.currentInviteCode()).isEqualTo("INV-NEXT-2026");
        verify(identityAdminSummaryApi).updateInviteCode("INV-NEXT-2026");
        verify(adminAuditService).record(
                eq(AdminAuditAction.INVITE_CODE_UPDATED),
                eq("ADMIN_SETTINGS"),
                eq(null),
                eq("Updated registration invite code"),
                eq(java.util.Map.of("inviteCodeLength", 13))
        );
    }

    @Test
    void shouldRotateCurrentInviteCodeForAdminSettings() {
        when(identityAdminSummaryApi.rotateInviteCode()).thenReturn("INV-ROTATED-2026");

        AdminRegistrationInviteCodeResponse response = adminMutableSettingsService.rotateRegistrationInviteCode();

        assertThat(response.currentInviteCode()).isEqualTo("INV-ROTATED-2026");
        verify(identityAdminSummaryApi).rotateInviteCode();
        verify(adminAuditService).record(
                eq(AdminAuditAction.INVITE_CODE_ROTATED),
                eq("ADMIN_SETTINGS"),
                eq(null),
                eq("Rotated registration invite code"),
                eq(java.util.Map.of("inviteCodeLength", 16))
        );
    }

    @Test
    void shouldUpdateOfflineTransferStorageLimit() {
        AdminOfflineTransferStorageLimitResponse expected = new AdminOfflineTransferStorageLimitResponse(1024L);
        when(adminMetricsService.updateOfflineTransferStorageLimit(1024L)).thenReturn(expected);

        AdminOfflineTransferStorageLimitResponse response = adminMutableSettingsService.updateOfflineTransferStorageLimit(1024L);

        assertThat(response).isSameAs(expected);
        verify(adminMetricsService).updateOfflineTransferStorageLimit(1024L);
        verify(adminAuditService).record(
                eq(AdminAuditAction.OFFLINE_TRANSFER_LIMIT_UPDATED),
                eq("ADMIN_SETTINGS"),
                eq(null),
                eq("Updated offline transfer storage limit"),
                eq(java.util.Map.of("offlineTransferStorageLimitBytes", 1024L))
        );
    }
}
