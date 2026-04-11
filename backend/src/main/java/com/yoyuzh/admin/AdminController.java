package com.yoyuzh.admin;

import com.yoyuzh.api.v2.tasks.BackgroundTaskResponse;
import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskFailureCategory;
import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminController {

    private final AdminService adminService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping("/summary")
    public ApiResponse<AdminSummaryResponse> summary() {
        return ApiResponse.success(adminService.getSummary());
    }

    @PatchMapping("/settings/offline-transfer-storage-limit")
    public ApiResponse<AdminOfflineTransferStorageLimitResponse> updateOfflineTransferStorageLimit(
            @Valid @RequestBody AdminOfflineTransferStorageLimitUpdateRequest request) {
        return ApiResponse.success(adminService.updateOfflineTransferStorageLimit(
                request.offlineTransferStorageLimitBytes()
        ));
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

    @GetMapping("/file-blobs")
    public ApiResponse<PageResponse<AdminFileBlobResponse>> fileBlobs(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "10") int size,
                                                                      @RequestParam(defaultValue = "") String userQuery,
                                                                      @RequestParam(required = false) Long storagePolicyId,
                                                                      @RequestParam(defaultValue = "") String objectKey,
                                                                      @RequestParam(required = false) FileEntityType entityType) {
        return ApiResponse.success(adminService.listFileBlobs(page, size, userQuery, storagePolicyId, objectKey, entityType));
    }

    @GetMapping("/shares")
    public ApiResponse<PageResponse<AdminShareResponse>> shares(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size,
                                                                @RequestParam(defaultValue = "") String userQuery,
                                                                @RequestParam(defaultValue = "") String fileName,
                                                                @RequestParam(defaultValue = "") String token,
                                                                @RequestParam(required = false) Boolean passwordProtected,
                                                                @RequestParam(required = false) Boolean expired) {
        return ApiResponse.success(adminService.listShares(page, size, userQuery, fileName, token, passwordProtected, expired));
    }

    @DeleteMapping("/shares/{shareId}")
    public ApiResponse<Void> deleteShare(@PathVariable Long shareId) {
        adminService.deleteShare(shareId);
        return ApiResponse.success();
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<AdminTaskResponse>> tasks(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String userQuery,
                                                              @RequestParam(required = false) BackgroundTaskType type,
                                                              @RequestParam(required = false) BackgroundTaskStatus status,
                                                              @RequestParam(required = false) BackgroundTaskFailureCategory failureCategory,
                                                              @RequestParam(required = false) AdminTaskLeaseState leaseState) {
        return ApiResponse.success(adminService.listTasks(page, size, userQuery, type, status, failureCategory, leaseState));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<AdminTaskResponse> task(@PathVariable Long taskId) {
        return ApiResponse.success(adminService.getTask(taskId));
    }

    @GetMapping("/storage-policies")
    public ApiResponse<List<AdminStoragePolicyResponse>> storagePolicies() {
        return ApiResponse.success(adminService.listStoragePolicies());
    }

    @PostMapping("/storage-policies")
    public ApiResponse<AdminStoragePolicyResponse> createStoragePolicy(
            @Valid @RequestBody AdminStoragePolicyUpsertRequest request) {
        return ApiResponse.success(adminService.createStoragePolicy(request));
    }

    @PutMapping("/storage-policies/{policyId}")
    public ApiResponse<AdminStoragePolicyResponse> updateStoragePolicy(
            @PathVariable Long policyId,
            @Valid @RequestBody AdminStoragePolicyUpsertRequest request) {
        return ApiResponse.success(adminService.updateStoragePolicy(policyId, request));
    }

    @PatchMapping("/storage-policies/{policyId}/status")
    public ApiResponse<AdminStoragePolicyResponse> updateStoragePolicyStatus(
            @PathVariable Long policyId,
            @Valid @RequestBody AdminStoragePolicyStatusUpdateRequest request) {
        return ApiResponse.success(adminService.updateStoragePolicyStatus(policyId, request.enabled()));
    }

    @PostMapping("/storage-policies/migrations")
    public ApiResponse<BackgroundTaskResponse> createStoragePolicyMigrationTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AdminStoragePolicyMigrationCreateRequest request) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiResponse.success(toTaskResponse(adminService.createStoragePolicyMigrationTask(user, request)));
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

    @PatchMapping("/users/{userId}/storage-quota")
    public ApiResponse<AdminUserResponse> updateUserStorageQuota(@PathVariable Long userId,
                                                                 @Valid @RequestBody AdminUserStorageQuotaUpdateRequest request) {
        return ApiResponse.success(adminService.updateUserStorageQuota(userId, request.storageQuotaBytes()));
    }

    @PatchMapping("/users/{userId}/max-upload-size")
    public ApiResponse<AdminUserResponse> updateUserMaxUploadSize(@PathVariable Long userId,
                                                                  @Valid @RequestBody AdminUserMaxUploadSizeUpdateRequest request) {
        return ApiResponse.success(adminService.updateUserMaxUploadSize(userId, request.maxUploadSizeBytes()));
    }

    @PostMapping("/users/{userId}/password/reset")
    public ApiResponse<AdminPasswordResetResponse> resetUserPassword(@PathVariable Long userId) {
        return ApiResponse.success(adminService.resetUserPassword(userId));
    }

    private BackgroundTaskResponse toTaskResponse(BackgroundTask task) {
        return new BackgroundTaskResponse(
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getUserId(),
                task.getPublicStateJson(),
                task.getCorrelationId(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }
}
