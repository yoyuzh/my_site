package com.yoyuzh.ops.admin.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface AdminUserGovernanceApi {

    PageResponse<AdminUserResponse> listUsers(int page, int size, String query);

    AdminUserResponse updateUserRole(Long userId, AdminUserRole role);

    AdminUserResponse updateUserBanned(Long userId, boolean banned);

    AdminUserResponse updateUserPassword(Long userId, String newPassword);

    AdminUserResponse updateUserStorageQuota(Long userId, long storageQuotaBytes);

    AdminUserResponse updateUserMaxUploadSize(Long userId, long maxUploadSizeBytes);

    AdminPasswordResetResponse resetUserPassword(Long userId);
}
