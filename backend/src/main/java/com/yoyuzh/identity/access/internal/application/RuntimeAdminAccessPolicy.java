package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import com.yoyuzh.identity.access.internal.domain.ManagementRolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeAdminAccessPolicy implements AdminAccessPolicy {

    private final AdminRuntimeSettingsService adminRuntimeSettingsService;
    private final ManagementRolePolicy managementRolePolicy = new ManagementRolePolicy();

    @Override
    public boolean hasAdminAccess(Authentication authentication) {
        return managementRolePolicy.hasAdminAccess(
                authentication,
                adminRuntimeSettingsService.snapshot().registrationManagementRoles());
    }
}
