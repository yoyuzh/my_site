package com.yoyuzh.admin;

import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsGovernanceApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminSettingsController {

    private final AdminSettingsGovernanceApi adminSettingsGovernanceApi;

    @GetMapping("/settings")
    public ApiResponse<AdminSettingsResponse> settings() {
        return ApiResponse.success(adminSettingsGovernanceApi.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<AdminSettingsResponse> updateSettings(
            @Valid @RequestBody AdminSettingsUpdateRequest request) {
        return ApiResponse.success(adminSettingsGovernanceApi.updateSettings(request));
    }

    @PatchMapping("/settings/registration/invite-code")
    public ApiResponse<AdminRegistrationInviteCodeResponse> updateRegistrationInviteCode(
            @Valid @RequestBody AdminRegistrationInviteCodeUpdateRequest request) {
        return ApiResponse.success(adminSettingsGovernanceApi.updateRegistrationInviteCode(request.inviteCode()));
    }

    @PostMapping("/settings/registration/invite-code/rotate")
    public ApiResponse<AdminRegistrationInviteCodeResponse> rotateRegistrationInviteCode() {
        return ApiResponse.success(adminSettingsGovernanceApi.rotateRegistrationInviteCode());
    }

    @PatchMapping("/settings/offline-transfer-storage-limit")
    public ApiResponse<AdminOfflineTransferStorageLimitResponse> updateOfflineTransferStorageLimit(
            @Valid @RequestBody AdminOfflineTransferStorageLimitUpdateRequest request) {
        return ApiResponse.success(adminSettingsGovernanceApi.updateOfflineTransferStorageLimit(
                request.offlineTransferStorageLimitBytes()
        ));
    }
}
