package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyMigrationInput;
import com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService;
import com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyUpsertInput;
import com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyResponse;
import com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyQueryService;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return ApiResponse.success(adminStorageGovernanceService.createStoragePolicy(toInput(request)));
    }

    @PutMapping("/storage-policies/{policyId}")
    public ApiResponse<AdminStoragePolicyResponse> updateStoragePolicy(
            @PathVariable Long policyId,
            @Valid @RequestBody AdminStoragePolicyUpsertRequest request) {
        return ApiResponse.success(adminStorageGovernanceService.updateStoragePolicy(policyId, toInput(request)));
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
        Long userId = userDetailsService.loadUserId(userDetails.getUsername());
        return ApiResponse.success(toTaskResponse(adminStorageGovernanceService.createStoragePolicyMigrationTask(userId, toInput(request))));
    }

    private AdminStoragePolicyUpsertInput toInput(AdminStoragePolicyUpsertRequest request) {
        return new AdminStoragePolicyUpsertInput(
                request.name(),
                request.type(),
                request.bucketName(),
                request.endpoint(),
                request.region(),
                request.privateBucket(),
                request.prefix(),
                request.credentialMode(),
                request.maxSizeBytes(),
                request.capabilities(),
                request.enabled()
        );
    }

    private AdminStoragePolicyMigrationInput toInput(AdminStoragePolicyMigrationCreateRequest request) {
        return new AdminStoragePolicyMigrationInput(
                request.sourcePolicyId(),
                request.targetPolicyId(),
                request.correlationId()
        );
    }

    private BackgroundTaskResponse toTaskResponse(BackgroundTaskView task) {
        return new BackgroundTaskResponse(
                task.id(),
                task.type(),
                task.status(),
                task.userId(),
                task.publicStateJson(),
                task.correlationId(),
                task.errorMessage(),
                task.createdAt(),
                task.updatedAt(),
                task.finishedAt()
        );
    }
}
