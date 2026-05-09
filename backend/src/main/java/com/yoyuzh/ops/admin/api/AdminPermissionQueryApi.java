package com.yoyuzh.ops.admin.api;

import org.springframework.security.core.Authentication;

public interface AdminPermissionQueryApi {

    AdminPermissionResponse currentPermissions(Authentication authentication);
}
