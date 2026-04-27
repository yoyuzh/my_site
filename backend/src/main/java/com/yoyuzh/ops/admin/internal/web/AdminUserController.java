package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminPasswordResetResponse;
import com.yoyuzh.ops.admin.api.AdminUserResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.ops.admin.api.AdminUserGovernanceApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserGovernanceApi adminUserGovernanceApi;

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> users(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String query) {
        return ApiResponse.success(adminUserGovernanceApi.listUsers(page, size, query));
    }

    @PatchMapping("/users/{userId}/role")
    public ApiResponse<AdminUserResponse> updateUserRole(@PathVariable Long userId,
                                                         @Valid @RequestBody AdminUserRoleUpdateRequest request) {
        return ApiResponse.success(adminUserGovernanceApi.updateUserRole(userId, request.role()));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(@PathVariable Long userId,
                                                           @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ApiResponse.success(adminUserGovernanceApi.updateUserBanned(userId, request.banned()));
    }

    @PutMapping("/users/{userId}/password")
    public ApiResponse<AdminUserResponse> updateUserPassword(@PathVariable Long userId,
                                                             @Valid @RequestBody AdminUserPasswordUpdateRequest request) {
        return ApiResponse.success(adminUserGovernanceApi.updateUserPassword(userId, request.newPassword()));
    }

    @PatchMapping("/users/{userId}/storage-quota")
    public ApiResponse<AdminUserResponse> updateUserStorageQuota(@PathVariable Long userId,
                                                                 @Valid @RequestBody AdminUserStorageQuotaUpdateRequest request) {
        return ApiResponse.success(adminUserGovernanceApi.updateUserStorageQuota(userId, request.storageQuotaBytes()));
    }

    @PatchMapping("/users/{userId}/max-upload-size")
    public ApiResponse<AdminUserResponse> updateUserMaxUploadSize(@PathVariable Long userId,
                                                                  @Valid @RequestBody AdminUserMaxUploadSizeUpdateRequest request) {
        return ApiResponse.success(adminUserGovernanceApi.updateUserMaxUploadSize(userId, request.maxUploadSizeBytes()));
    }

    @PostMapping("/users/{userId}/password/reset")
    public ApiResponse<AdminPasswordResetResponse> resetUserPassword(@PathVariable Long userId) {
        return ApiResponse.success(adminUserGovernanceApi.resetUserPassword(userId));
    }
}
