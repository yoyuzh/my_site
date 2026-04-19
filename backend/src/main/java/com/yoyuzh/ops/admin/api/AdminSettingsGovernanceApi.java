package com.yoyuzh.ops.admin.api;

import com.yoyuzh.admin.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.admin.AdminRegistrationInviteCodeResponse;
import com.yoyuzh.admin.AdminSettingsResponse;
import com.yoyuzh.admin.AdminSettingsUpdateRequest;

public interface AdminSettingsGovernanceApi {

    AdminSettingsResponse getSettings();

    AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request);

    AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode);

    AdminRegistrationInviteCodeResponse rotateRegistrationInviteCode();

    AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes);
}
