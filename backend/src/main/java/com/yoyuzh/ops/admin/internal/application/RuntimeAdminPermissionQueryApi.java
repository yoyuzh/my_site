package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import com.yoyuzh.ops.admin.api.AdminPermissionCode;
import com.yoyuzh.ops.admin.api.AdminPermissionQueryApi;
import com.yoyuzh.ops.admin.api.AdminPermissionResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeAdminPermissionQueryApi implements AdminPermissionQueryApi {

    private final AdminAccessPolicy adminAccessPolicy;

    @Override
    public AdminPermissionResponse currentPermissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !adminAccessPolicy.hasAdminAccess(authentication)) {
            return new AdminPermissionResponse(List.of());
        }
        return new AdminPermissionResponse(Arrays.stream(AdminPermissionCode.values())
                .map(AdminPermissionCode::code)
                .toList());
    }
}
