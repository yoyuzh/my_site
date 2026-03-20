package com.yoyuzh.admin;

import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/summary")
    public ApiResponse<AdminSummaryResponse> summary() {
        return ApiResponse.success(adminService.getSummary());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> users(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String query) {
        return ApiResponse.success(adminService.listUsers(page, size, query));
    }

    @GetMapping("/files")
    public ApiResponse<PageResponse<AdminFileResponse>> files(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String query,
                                                              @RequestParam(defaultValue = "") String ownerQuery) {
        return ApiResponse.success(adminService.listFiles(page, size, query, ownerQuery));
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
        adminService.deleteFile(fileId);
        return ApiResponse.success();
    }

    @PatchMapping("/users/{userId}/role")
    public ApiResponse<AdminUserResponse> updateUserRole(@PathVariable Long userId,
                                                         @Valid @RequestBody AdminUserRoleUpdateRequest request) {
        return ApiResponse.success(adminService.updateUserRole(userId, request.role()));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(@PathVariable Long userId,
                                                           @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ApiResponse.success(adminService.updateUserBanned(userId, request.banned()));
    }

    @PutMapping("/users/{userId}/password")
    public ApiResponse<AdminUserResponse> updateUserPassword(@PathVariable Long userId,
                                                             @Valid @RequestBody AdminUserPasswordUpdateRequest request) {
        return ApiResponse.success(adminService.updateUserPassword(userId, request.newPassword()));
    }

    @PostMapping("/users/{userId}/password/reset")
    public ApiResponse<AdminPasswordResetResponse> resetUserPassword(@PathVariable Long userId) {
        return ApiResponse.success(adminService.resetUserPassword(userId));
    }
}
