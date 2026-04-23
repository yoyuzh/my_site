package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.ops.admin.api.AdminRuntimeSettingsApi;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.identity.access.api.AdminAccessContinuityGuard;
import com.yoyuzh.identity.access.internal.domain.ManagementRolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RuntimeAdminAccessContinuityGuard implements AdminAccessContinuityGuard {

    private final UserRepository userRepository;
    private final AdminRuntimeSettingsApi adminRuntimeSettingsApi;
    private final ManagementRolePolicy managementRolePolicy = new ManagementRolePolicy();

    @Override
    public void ensureAdminAccessRemainsAvailable(
            String currentRole,
            boolean currentlyBanned,
            String nextRole,
            boolean bannedAfterUpdate) {
        Set<UserRole> adminCapableRoles = resolveAdminCapableRoles();
        if (adminCapableRoles.isEmpty()) {
            return;
        }

        boolean currentlyAdminCapable =
                !currentlyBanned && isAdminCapableRole(currentRole, adminCapableRoles);
        boolean adminCapableAfterUpdate =
                !bannedAfterUpdate && isAdminCapableRole(nextRole, adminCapableRoles);
        if (!currentlyAdminCapable || adminCapableAfterUpdate) {
            return;
        }

        long adminCapableUserCount = userRepository.countByBannedFalseAndRoleIn(adminCapableRoles);
        if (adminCapableUserCount <= 1) {
            throw new BusinessException(ErrorCode.UNKNOWN, "at least one unbanned admin-capable user must remain");
        }
    }

    private Set<UserRole> resolveAdminCapableRoles() {
        Set<String> normalizedRoleNames = managementRolePolicy.normalizeConfiguredRoles(
                adminRuntimeSettingsApi.registrationManagementRoles());
        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String normalizedRoleName : normalizedRoleNames) {
            try {
                roles.add(UserRole.valueOf(normalizedRoleName));
            } catch (IllegalArgumentException ignored) {
                // Ignore unsupported runtime role values; they do not map to a backend user role.
            }
        }
        return roles;
    }

    private boolean isAdminCapableRole(String roleName, Set<UserRole> adminCapableRoles) {
        if (roleName == null) {
            return false;
        }
        try {
            return adminCapableRoles.contains(UserRole.valueOf(roleName));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
