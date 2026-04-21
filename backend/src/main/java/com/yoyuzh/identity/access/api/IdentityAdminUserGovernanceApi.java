package com.yoyuzh.identity.access.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface IdentityAdminUserGovernanceApi {

    PageResponse<IdentityAdminUserView> listUsersAsAdmin(IdentityAdminUserQuery query);

    IdentityAdminUserView updateUserRoleAsAdmin(Long userId, IdentityRoleName role);

    IdentityAdminUserView updateUserBannedAsAdmin(Long userId, boolean banned);

    IdentityAdminUserView updateUserPasswordAsAdmin(Long userId, String newPassword);

    IdentityAdminUserView updateUserStorageQuotaAsAdmin(Long userId, long storageQuotaBytes);

    IdentityAdminUserView updateUserMaxUploadSizeAsAdmin(Long userId, long maxUploadSizeBytes);
}
