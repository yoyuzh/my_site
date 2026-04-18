package com.yoyuzh.admin;

import com.yoyuzh.api.v2.tasks.BackgroundTaskResponse;
import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.files.tasks.BackgroundTask;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminStoragePolicyController {

    private final AdminStoragePolicyQueryService adminStoragePolicyQueryService;
    private final AdminStorageGovernanceService adminStorageGovernanceService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping("/storage-policies")
    public ApiResponse<List<AdminStoragePolicyResponse>> storagePolicies() {
        return ApiResponse.success(adminStoragePolicyQueryService.listStoragePolicies());
    }

    @PostMapping("/storage-policies")
    public ApiResponse<AdminStoragePolicyResponse> createStoragePolicy(
            @Valid @RequestBody AdminStoragePolicyUpsertRequest request) {
        return ApiResponse.success(adminStorageGovernanceService.createStoragePolicy(request));
    }

    @PutMapping("/storage-policies/{policyId}")
    public ApiResponse<AdminStoragePolicyResponse> updateStoragePolicy(
            @PathVariable Long policyId,
            @Valid @RequestBody AdminStoragePolicyUpsertRequest request) {
        return ApiResponse.success(adminStorageGovernanceService.updateStoragePolicy(policyId, request));
    }

    @PatchMapping("/storage-policies/{policyId}/status")
    public ApiResponse<AdminStoragePolicyResponse> updateStoragePolicyStatus(
            @PathVariable Long policyId,
            @Valid @RequestBody AdminStoragePolicyStatusUpdateRequest request) {
        return ApiResponse.success(adminStorageGovernanceService.updateStoragePolicyStatus(policyId, request.enabled()));
    }

    @PostMapping("/storage-policies/migrations")
    public ApiResponse<BackgroundTaskResponse> createStoragePolicyMigrationTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AdminStoragePolicyMigrationCreateRequest request) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiResponse.success(toTaskResponse(adminStorageGovernanceService.createStoragePolicyMigrationTask(user, request)));
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
