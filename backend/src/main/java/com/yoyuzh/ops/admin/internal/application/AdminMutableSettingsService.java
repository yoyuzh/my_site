package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse;
import com.yoyuzh.ops.admin.api.AdminRegistrationInviteCodeResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMutableSettingsService {

    private final IdentityAdminSummaryApi identityAdminSummaryApi;
    private final AdminMetricsService adminMetricsService;
    private final AdminRuntimeSettingsService adminRuntimeSettingsService;
    private final AdminConfigSnapshotService adminConfigSnapshotService;

    @Transactional
    public AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request) {
        if (!request.hasWritableSections()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "at least one writable section is required");
        }
        adminRuntimeSettingsService.update(toWritableRuntimeUpdateRequest(request));
        if (request.transfer() != null) {
            adminMetricsService.updateOfflineTransferStorageLimit(request.transfer().offlineTransferStorageLimitBytes());
        }
        return adminConfigSnapshotService.getSettings();
    }

    @Transactional
    public AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode) {
        String normalizedInviteCode = inviteCode == null ? "" : inviteCode.trim();
        String currentInviteCode = identityAdminSummaryApi.updateInviteCode(normalizedInviteCode);
        return new AdminRegistrationInviteCodeResponse(currentInviteCode);
    }

    @Transactional
    public AdminRegistrationInviteCodeResponse rotateRegistrationInviteCode() {
        String currentInviteCode = identityAdminSummaryApi.rotateInviteCode();
        return new AdminRegistrationInviteCodeResponse(currentInviteCode);
    }

    @Transactional
    public AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes) {
        return adminMetricsService.updateOfflineTransferStorageLimit(offlineTransferStorageLimitBytes);
    }

    private AdminSettingsUpdateRequest toWritableRuntimeUpdateRequest(AdminSettingsUpdateRequest request) {
        AdminSettingsUpdateRequest.RegistrationSection registrationSection = request.registration();
        if (registrationSection != null) {
            registrationSection = new AdminSettingsUpdateRequest.RegistrationSection(
                    registrationSection.inviteCodeRequired(),
                    identityAdminSummaryApi.currentInviteCode(),
                    registrationSection.managementRoles()
            );
        }
        return new AdminSettingsUpdateRequest(
                null,
                registrationSection,
                null,
                request.transfer(),
                null,
                null,
                null,
                null
        );
    }
}
