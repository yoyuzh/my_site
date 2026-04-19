package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.api.AdminPasswordResetResponse;
import com.yoyuzh.ops.admin.api.AdminUserRole;
import com.yoyuzh.ops.admin.api.AdminUserResponse;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.ops.admin.api.AdminUserGovernanceApi;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAdminUserGovernanceApi implements AdminUserGovernanceApi {

    private final AdminUserGovernanceService adminUserGovernanceService;

    public RuntimeAdminUserGovernanceApi(AdminUserGovernanceService adminUserGovernanceService) {
        this.adminUserGovernanceService = adminUserGovernanceService;
    }

    @Override
    public PageResponse<AdminUserResponse> listUsers(int page, int size, String query) {
        return adminUserGovernanceService.listUsers(page, size, query);
    }

    @Override
    public AdminUserResponse updateUserRole(Long userId, AdminUserRole role) {
        UserRole legacyRole = UserRole.valueOf(role.name());
        return adminUserGovernanceService.updateUserRole(userId, legacyRole);
    }

    @Override
    public AdminUserResponse updateUserBanned(Long userId, boolean banned) {
        return adminUserGovernanceService.updateUserBanned(userId, banned);
    }

    @Override
    public AdminUserResponse updateUserPassword(Long userId, String newPassword) {
        return adminUserGovernanceService.updateUserPassword(userId, newPassword);
    }

    @Override
    public AdminUserResponse updateUserStorageQuota(Long userId, long storageQuotaBytes) {
        return adminUserGovernanceService.updateUserStorageQuota(userId, storageQuotaBytes);
    }

    @Override
    public AdminUserResponse updateUserMaxUploadSize(Long userId, long maxUploadSizeBytes) {
        return adminUserGovernanceService.updateUserMaxUploadSize(userId, maxUploadSizeBytes);
    }

    @Override
    public AdminPasswordResetResponse resetUserPassword(Long userId) {
        return adminUserGovernanceService.resetUserPassword(userId);
    }
}
