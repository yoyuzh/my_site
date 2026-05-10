package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminPermissionQueryApi;
import com.yoyuzh.ops.admin.api.AdminPermissionResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionQueryApi adminPermissionQueryApi;

    @GetMapping("/permissions")
    public ApiResponse<AdminPermissionResponse> permissions(Authentication authentication) {
        return ApiResponse.success(adminPermissionQueryApi.currentPermissions(authentication));
    }
}
