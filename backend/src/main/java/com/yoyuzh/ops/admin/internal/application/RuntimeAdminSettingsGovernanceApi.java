package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.admin.AdminConfigSnapshotService;
import com.yoyuzh.admin.AdminMutableSettingsService;
import com.yoyuzh.admin.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.admin.AdminRegistrationInviteCodeResponse;
import com.yoyuzh.admin.AdminSettingsResponse;
import com.yoyuzh.admin.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.api.AdminSettingsGovernanceApi;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAdminSettingsGovernanceApi implements AdminSettingsGovernanceApi {

    private final AdminConfigSnapshotService adminConfigSnapshotService;
    private final AdminMutableSettingsService adminMutableSettingsService;

    public RuntimeAdminSettingsGovernanceApi(AdminConfigSnapshotService adminConfigSnapshotService,
                                             AdminMutableSettingsService adminMutableSettingsService) {
        this.adminConfigSnapshotService = adminConfigSnapshotService;
        this.adminMutableSettingsService = adminMutableSettingsService;
    }

    @Override
    public AdminSettingsResponse getSettings() {
        return adminConfigSnapshotService.getSettings();
    }

    @Override
    public AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request) {
        return adminMutableSettingsService.updateSettings(request);
    }

    @Override
    public AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode) {
        return adminMutableSettingsService.updateRegistrationInviteCode(inviteCode);
    }

    @Override
    public AdminRegistrationInviteCodeResponse rotateRegistrationInviteCode() {
        return adminMutableSettingsService.rotateRegistrationInviteCode();
    }

    @Override
    public AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes) {
        return adminMutableSettingsService.updateOfflineTransferStorageLimit(offlineTransferStorageLimitBytes);
    }
}
