package com.yoyuzh.ops.admin.api;

public interface AdminSettingsGovernanceApi {

    AdminSettingsResponse getSettings();

    AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request);

    AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode);

    AdminRegistrationInviteCodeResponse rotateRegistrationInviteCode();

    AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes);
}
