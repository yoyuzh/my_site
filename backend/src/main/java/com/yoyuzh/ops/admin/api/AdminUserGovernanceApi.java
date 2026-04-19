package com.yoyuzh.ops.admin.api;

import com.yoyuzh.admin.AdminPasswordResetResponse;
import com.yoyuzh.admin.AdminUserResponse;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.PageResponse;

public interface AdminUserGovernanceApi {

    PageResponse<AdminUserResponse> listUsers(int page, int size, String query);

    AdminUserResponse updateUserRole(Long userId, UserRole role);

    AdminUserResponse updateUserBanned(Long userId, boolean banned);

    AdminUserResponse updateUserPassword(Long userId, String newPassword);

    AdminUserResponse updateUserStorageQuota(Long userId, long storageQuotaBytes);

    AdminUserResponse updateUserMaxUploadSize(Long userId, long maxUploadSizeBytes);

    AdminPasswordResetResponse resetUserPassword(Long userId);
}
