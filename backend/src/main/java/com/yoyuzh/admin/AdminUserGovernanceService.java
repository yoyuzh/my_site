package com.yoyuzh.admin;

import com.yoyuzh.auth.AuthSessionPolicy;
import com.yoyuzh.auth.AuthTokenInvalidationService;
import com.yoyuzh.auth.PasswordPolicy;
import com.yoyuzh.auth.RefreshTokenService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserGovernanceService {

    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenInvalidationService authTokenInvalidationService;
    private final AuthSessionPolicy authSessionPolicy;
    private final AdminAuditService adminAuditService;
    private final AdminRuntimeSettingsService adminRuntimeSettingsService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PageResponse<AdminUserResponse> listUsers(int page, int size, String query) {
        Page<User> result = userRepository.searchByUsernameOrEmail(
                normalizeQuery(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<User> users = result.getContent();
        Map<Long, Long> usedStorageByUserId = loadUsedStorageByUserIds(users);
        return new PageResponse<>(
                users.stream()
                        .map(user -> toUserResponse(user, usedStorageByUserId.getOrDefault(user.getId(), 0L)))
                        .toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, UserRole role) {
        User user = getRequiredUser(userId);
        ensureAdminAccessRemainsAvailable(user, false, role);
        user.setRole(role);
        AdminUserResponse response = toUserResponse(userRepository.save(user));
        adminAuditService.record(
                AdminAuditAction.UPDATE_USER_ROLE,
                "USER",
                userId,
                "Updated user role",
                Map.of("role", role.name())
        );
        return response;
    }

    @Transactional
    public AdminUserResponse updateUserBanned(Long userId, boolean banned) {
        User user = getRequiredUser(userId);
        ensureAdminAccessRemainsAvailable(user, banned, user.getRole());
        user.setBanned(banned);
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId());
        authSessionPolicy.rotateAllActiveSessions(user);
        refreshTokenService.revokeAllForUser(user.getId());
        AdminUserResponse response = toUserResponse(userRepository.save(user));
        adminAuditService.record(
                AdminAuditAction.UPDATE_USER_BANNED,
                "USER",
                userId,
                banned ? "Banned user" : "Unbanned user",
                Map.of("banned", banned)
        );
        return response;
    }

    @Transactional
    public AdminUserResponse updateUserPassword(Long userId, String newPassword) {
        return updateUserPasswordInternal(userId, newPassword, AdminAuditAction.UPDATE_USER_PASSWORD);
    }

    @Transactional
    public AdminUserResponse updateUserStorageQuota(Long userId, long storageQuotaBytes) {
        User user = getRequiredUser(userId);
        user.setStorageQuotaBytes(storageQuotaBytes);
        AdminUserResponse response = toUserResponse(userRepository.save(user));
        adminAuditService.record(
                AdminAuditAction.UPDATE_USER_STORAGE_QUOTA,
                "USER",
                userId,
                "Updated user storage quota",
                Map.of("storageQuotaBytes", storageQuotaBytes)
        );
        return response;
    }

    @Transactional
    public AdminUserResponse updateUserMaxUploadSize(Long userId, long maxUploadSizeBytes) {
        User user = getRequiredUser(userId);
        user.setMaxUploadSizeBytes(maxUploadSizeBytes);
        AdminUserResponse response = toUserResponse(userRepository.save(user));
        adminAuditService.record(
                AdminAuditAction.UPDATE_USER_MAX_UPLOAD_SIZE,
                "USER",
                userId,
                "Updated user max upload size",
                Map.of("maxUploadSizeBytes", maxUploadSizeBytes)
        );
        return response;
    }

    @Transactional
    public AdminPasswordResetResponse resetUserPassword(Long userId) {
        String temporaryPassword = generateTemporaryPassword();
        updateUserPasswordInternal(userId, temporaryPassword, AdminAuditAction.RESET_USER_PASSWORD);
        return new AdminPasswordResetResponse(temporaryPassword);
    }

    private AdminUserResponse updateUserPasswordInternal(Long userId, String newPassword, AdminAuditAction action) {
        if (!PasswordPolicy.isStrong(newPassword)) {
            throw new BusinessException(ErrorCode.UNKNOWN, PasswordPolicy.VALIDATION_MESSAGE);
        }
        User user = getRequiredUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId());
        authSessionPolicy.rotateAllActiveSessions(user);
        refreshTokenService.revokeAllForUser(user.getId());
        AdminUserResponse response = toUserResponse(userRepository.save(user));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("passwordLength", newPassword.length());
        details.put("temporaryPassword", action == AdminAuditAction.RESET_USER_PASSWORD);
        adminAuditService.record(
                action,
                "USER",
                userId,
                action == AdminAuditAction.RESET_USER_PASSWORD
                        ? "Reset user password"
                        : "Updated user password",
                details
        );
        return response;
    }

    private AdminUserResponse toUserResponse(User user) {
        return toUserResponse(user, storedFileRepository.sumFileSizeByUserId(user.getId()));
    }

    private AdminUserResponse toUserResponse(User user, long usedStorageBytes) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getRole(),
                user.isBanned(),
                usedStorageBytes,
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    private Map<Long, Long> loadUsedStorageByUserIds(List<User> users) {
        Set<Long> userIds = users.stream()
                .map(User::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.sumFileSizeByUserIds(userIds).stream()
                .collect(Collectors.toMap(
                        StoredFileRepository.UserStorageUsageProjection::getUserId,
                        projection -> projection.getUsedStorageBytes() == null ? 0L : projection.getUsedStorageBytes()
                ));
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "user not found"));
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private void ensureAdminAccessRemainsAvailable(User user, boolean bannedAfterUpdate, UserRole roleAfterUpdate) {
        Set<UserRole> adminCapableRoles = resolveAdminCapableRoles();
        if (adminCapableRoles.isEmpty()) {
            return;
        }

        boolean currentlyAdminCapable = !user.isBanned() && adminCapableRoles.contains(user.getRole());
        boolean adminCapableAfterUpdate = !bannedAfterUpdate && adminCapableRoles.contains(roleAfterUpdate);
        if (!currentlyAdminCapable || adminCapableAfterUpdate) {
            return;
        }

        long adminCapableUserCount = userRepository.countByBannedFalseAndRoleIn(adminCapableRoles);
        if (adminCapableUserCount <= 1) {
            throw new BusinessException(ErrorCode.UNKNOWN, "at least one unbanned admin-capable user must remain");
        }
    }

    private Set<UserRole> resolveAdminCapableRoles() {
        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String configuredRole : adminRuntimeSettingsService.snapshot().registrationManagementRoles()) {
            String normalizedRole = AdminRuntimeSettingsService.normalizeManagementRole(configuredRole);
            if (normalizedRole == null) {
                continue;
            }
            try {
                roles.add(UserRole.valueOf(normalizedRole));
            } catch (IllegalArgumentException ignored) {
                // Ignore unsupported runtime role values; they do not map to a backend user role.
            }
        }
        return roles;
    }

    private String generateTemporaryPassword() {
        String lowers = "abcdefghjkmnpqrstuvwxyz";
        String uppers = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String digits = "23456789";
        String specials = "!@#$%^&*";
        String all = lowers + uppers + digits + specials;
        char[] password = new char[12];
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
}
