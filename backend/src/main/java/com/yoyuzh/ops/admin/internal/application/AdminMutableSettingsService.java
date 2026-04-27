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

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminMutableSettingsService {

    private final IdentityAdminSummaryApi identityAdminSummaryApi;
    private final AdminMetricsService adminMetricsService;
    private final AdminAuditService adminAuditService;
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
        AdminSettingsResponse response = adminConfigSnapshotService.getSettings();

        adminAuditService.record(
                AdminAuditAction.UPDATE_SYSTEM_SETTINGS,
                "SYSTEM_SETTING",
                null,
                "Updated admin settings snapshot",
                buildSettingsAuditDetails(response, request)
        );
        return response;
    }

    @Transactional
    public AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode) {
        String normalizedInviteCode = inviteCode == null ? "" : inviteCode.trim();
        String currentInviteCode = identityAdminSummaryApi.updateInviteCode(normalizedInviteCode);
        adminAuditService.record(
                AdminAuditAction.UPDATE_REGISTRATION_INVITE_CODE,
                "SYSTEM_SETTING",
                null,
                "Updated registration invite code",
                Map.of("inviteCodeLength", currentInviteCode.length())
        );
        return new AdminRegistrationInviteCodeResponse(currentInviteCode);
    }

    @Transactional
    public AdminRegistrationInviteCodeResponse rotateRegistrationInviteCode() {
        String currentInviteCode = identityAdminSummaryApi.rotateInviteCode();
        adminAuditService.record(
                AdminAuditAction.ROTATE_REGISTRATION_INVITE_CODE,
                "SYSTEM_SETTING",
                null,
                "Rotated registration invite code",
                Map.of("inviteCodeLength", currentInviteCode.length())
        );
        return new AdminRegistrationInviteCodeResponse(currentInviteCode);
    }

    @Transactional
    public AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes) {
        AdminOfflineTransferStorageLimitResponse response = adminMetricsService.updateOfflineTransferStorageLimit(
                offlineTransferStorageLimitBytes
        );
        adminAuditService.record(
                AdminAuditAction.UPDATE_OFFLINE_TRANSFER_STORAGE_LIMIT,
                "SYSTEM_SETTING",
                null,
                "Updated offline transfer storage limit",
                Map.of("offlineTransferStorageLimitBytes", response.offlineTransferStorageLimitBytes())
        );
        return response;
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

    private Map<String, Object> buildSettingsAuditDetails(AdminSettingsResponse response, AdminSettingsUpdateRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("inviteCodeRequired", response.registration().inviteCodeRequired());
        details.put("managementRoleCount", response.registration().managementRoles().size());
        details.put("offlineTransferStorageLimitBytes", response.transfer().offlineTransferStorageLimitBytes());
        details.put("readOnlySectionsIgnored", true);
        details.put("registrationUpdated", request.registration() != null);
        details.put("transferUpdated", request.transfer() != null);
        return details;
    }
}
