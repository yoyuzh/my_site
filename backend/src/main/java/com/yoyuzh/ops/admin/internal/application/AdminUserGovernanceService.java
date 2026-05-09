package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.identity.access.api.IdentityAdminUserGovernanceApi;
import com.yoyuzh.identity.access.api.IdentityAdminUserQuery;
import com.yoyuzh.identity.access.api.IdentityAdminUserView;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.ops.admin.api.AdminPasswordResetResponse;
import com.yoyuzh.ops.admin.api.AdminUserRole;
import com.yoyuzh.ops.admin.api.AdminUserResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserGovernanceService {

    private static final int TEMPORARY_PASSWORD_LENGTH = 16;

    private final IdentityAdminUserGovernanceApi identityAdminUserGovernanceApi;
    private final WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    private final AdminAuditService adminAuditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PageResponse<AdminUserResponse> listUsers(int page, int size, String query) {
        PageResponse<IdentityAdminUserView> users = identityAdminUserGovernanceApi.listUsersAsAdmin(
                new IdentityAdminUserQuery(page, size, normalizeQuery(query))
        );
        Map<Long, Long> usedStorageByUserId = loadUsedStorageByUserIds(users.items());
        return new PageResponse<>(
                users.items().stream()
                        .map(user -> toUserResponse(user, usedStorageByUserId.getOrDefault(user.id(), 0L)))
                        .toList(),
                users.total(),
                page,
                size
        );
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, AdminUserRole role) {
        IdentityAdminUserView user = identityAdminUserGovernanceApi.updateUserRoleAsAdmin(
                userId,
                role == null ? null : IdentityRoleName.valueOf(role.name())
        );
        AdminUserResponse response = toUserResponse(user, workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(userId));
        adminAuditService.record(
                AdminAuditAction.USER_ROLE_UPDATED,
                "USER",
                userId,
                "Updated user role",
                Map.of("role", role == null ? null : role.name())
        );
        return response;
    }

    @Transactional
    public AdminUserResponse updateUserBanned(Long userId, boolean banned) {
        IdentityAdminUserView user = identityAdminUserGovernanceApi.updateUserBannedAsAdmin(userId, banned);
        AdminUserResponse response = toUserResponse(user, workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(userId));
        adminAuditService.record(
                AdminAuditAction.USER_STATUS_UPDATED,
                "USER",
                userId,
                banned ? "Banned user" : "Unbanned user",
                Map.of("banned", banned)
        );
        return response;
    }

    @Transactional
    public AdminUserResponse updateUserPassword(Long userId, String newPassword) {
        return updateUserPasswordInternal(userId, newPassword, AdminAuditAction.USER_PASSWORD_UPDATED);
    }

    @Transactional
    public AdminPasswordResetResponse resetUserPassword(Long userId) {
        String temporaryPassword = generateTemporaryPassword();
        updateUserPasswordInternal(userId, temporaryPassword, AdminAuditAction.USER_PASSWORD_RESET);
        return new AdminPasswordResetResponse(temporaryPassword);
    }

    private AdminUserResponse updateUserPasswordInternal(Long userId, String newPassword, AdminAuditAction action) {
        IdentityAdminUserView user = identityAdminUserGovernanceApi.updateUserPasswordAsAdmin(userId, newPassword);
        AdminUserResponse response = toUserResponse(user, workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(userId));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("temporaryPassword", action == AdminAuditAction.USER_PASSWORD_RESET);
        adminAuditService.record(
                action,
                "USER",
                userId,
                action == AdminAuditAction.USER_PASSWORD_RESET
                        ? "Reset user password"
                        : "Updated user password",
                details
        );
        return response;
    }

    private AdminUserResponse toUserResponse(IdentityAdminUserView user, long usedStorageBytes) {
        return new AdminUserResponse(
                user.id(),
                user.username(),
                user.email(),
                user.phoneNumber(),
                user.createdAt(),
                user.role() == null ? null : AdminUserRole.valueOf(user.role().name()),
                user.banned(),
                usedStorageBytes,
                user.storageQuotaBytes(),
                user.maxUploadSizeBytes()
        );
    }

    private Map<Long, Long> loadUsedStorageByUserIds(List<IdentityAdminUserView> users) {
        Set<Long> userIds = users.stream()
                .map(IdentityAdminUserView::id)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return workspaceAdminGovernanceApi.loadUsedStorageBytesByUserIds(userIds);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private String generateTemporaryPassword() {
        String lowers = "abcdefghjkmnpqrstuvwxyz";
        String uppers = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String digits = "23456789";
        String specials = "!@#$%^&*";
        String all = lowers + uppers + digits + specials;
        char[] password = new char[TEMPORARY_PASSWORD_LENGTH];
        password[0] = lowers.charAt(secureRandom.nextInt(lowers.length()));
        password[1] = uppers.charAt(secureRandom.nextInt(uppers.length()));
        password[2] = digits.charAt(secureRandom.nextInt(digits.length()));
        password[3] = specials.charAt(secureRandom.nextInt(specials.length()));
        for (int i = 4; i < password.length; i += 1) {
            password[i] = all.charAt(secureRandom.nextInt(all.length()));
        }
        for (int i = password.length - 1; i > 0; i -= 1) {
            int j = secureRandom.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }

    @Transactional
    public AdminUserResponse updateUserStorageQuota(Long userId, long storageQuotaBytes) {
        IdentityAdminUserView user =
                identityAdminUserGovernanceApi.updateUserStorageQuotaAsAdmin(userId, storageQuotaBytes);
        AdminUserResponse response = toUserResponse(user, workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(userId));
        adminAuditService.record(
                AdminAuditAction.USER_STORAGE_QUOTA_UPDATED,
                "USER",
                userId,
                "Updated user storage quota",
                Map.of("storageQuotaBytes", storageQuotaBytes)
        );
        return response;
    }

    @Transactional
    public AdminUserResponse updateUserMaxUploadSize(Long userId, long maxUploadSizeBytes) {
        IdentityAdminUserView user =
                identityAdminUserGovernanceApi.updateUserMaxUploadSizeAsAdmin(userId, maxUploadSizeBytes);
        AdminUserResponse response = toUserResponse(user, workspaceAdminGovernanceApi.loadUsedStorageBytesByUserId(userId));
        adminAuditService.record(
                AdminAuditAction.USER_MAX_UPLOAD_SIZE_UPDATED,
                "USER",
                userId,
                "Updated user max upload size",
                Map.of("maxUploadSizeBytes", maxUploadSizeBytes)
        );
        return response;
    }
}
