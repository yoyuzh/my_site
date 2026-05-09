package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.ops.admin.api.AdminRegistrationInviteCodeResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.api.AdminSettingsGovernanceApi;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeAdminSettingsGovernanceApi implements AdminSettingsGovernanceApi {

    private final AdminConfigSnapshotService adminConfigSnapshotService;
    private final AdminMutableSettingsService adminMutableSettingsService;
    private final AdminAuditService adminAuditService;

    public RuntimeAdminSettingsGovernanceApi(AdminConfigSnapshotService adminConfigSnapshotService,
                                             AdminMutableSettingsService adminMutableSettingsService,
                                             AdminAuditService adminAuditService) {
        this.adminConfigSnapshotService = adminConfigSnapshotService;
        this.adminMutableSettingsService = adminMutableSettingsService;
        this.adminAuditService = adminAuditService;
    }

    @Override
    public AdminSettingsResponse getSettings() {
        return adminConfigSnapshotService.getSettings();
    }

    @Override
    public AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request) {
        AdminSettingsResponse response = adminMutableSettingsService.updateSettings(request);
        adminAuditService.record(
                AdminAuditAction.SETTINGS_UPDATED,
                "ADMIN_SETTINGS",
                null,
                "Updated admin settings",
                buildSettingsAuditDetails(response, request)
        );
        return response;
    }

    @Override
    public AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode) {
        AdminRegistrationInviteCodeResponse response = adminMutableSettingsService.updateRegistrationInviteCode(inviteCode);
        adminAuditService.record(
                AdminAuditAction.INVITE_CODE_UPDATED,
                "ADMIN_SETTINGS",
                null,
                "Updated registration invite code",
                Map.of("inviteCodeLength", response.currentInviteCode() == null ? 0 : response.currentInviteCode().length())
        );
        return response;
    }

    @Override
    public AdminRegistrationInviteCodeResponse rotateRegistrationInviteCode() {
        AdminRegistrationInviteCodeResponse response = adminMutableSettingsService.rotateRegistrationInviteCode();
        adminAuditService.record(
                AdminAuditAction.INVITE_CODE_ROTATED,
                "ADMIN_SETTINGS",
                null,
                "Rotated registration invite code",
                Map.of("inviteCodeLength", response.currentInviteCode() == null ? 0 : response.currentInviteCode().length())
        );
        return response;
    }

    @Override
    public AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes) {
        AdminOfflineTransferStorageLimitResponse response =
                adminMutableSettingsService.updateOfflineTransferStorageLimit(offlineTransferStorageLimitBytes);
        adminAuditService.record(
                AdminAuditAction.OFFLINE_TRANSFER_LIMIT_UPDATED,
                "ADMIN_SETTINGS",
                null,
                "Updated offline transfer storage limit",
                Map.of("offlineTransferStorageLimitBytes", response.offlineTransferStorageLimitBytes())
        );
        return response;
    }

    private Map<String, Object> buildSettingsAuditDetails(
            AdminSettingsResponse response,
            AdminSettingsUpdateRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("inviteCodeRequired", response.registration().inviteCodeRequired());
        details.put("managementRoleCount", response.registration().managementRoles().size());
        details.put("offlineTransferStorageLimitBytes", response.transfer().offlineTransferStorageLimitBytes());
        details.put("registrationUpdated", request.registration() != null);
        details.put("transferUpdated", request.transfer() != null);
        return details;
    }
}
