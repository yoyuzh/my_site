package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminMutableSettingsService {

    private final RegistrationInviteService registrationInviteService;
    private final AdminMetricsService adminMetricsService;
    private final AdminAuditService adminAuditService;

    @Transactional
    public AdminRegistrationInviteCodeResponse updateRegistrationInviteCode(String inviteCode) {
        String normalizedInviteCode = normalizeQuery(inviteCode);
        String currentInviteCode = registrationInviteService.updateCurrentInviteCode(normalizedInviteCode);
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
        String currentInviteCode = registrationInviteService.rotateCurrentInviteCode();
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

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }
}
