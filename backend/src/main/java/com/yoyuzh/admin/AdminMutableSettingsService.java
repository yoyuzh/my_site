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
    private final AdminRuntimeSettingsService adminRuntimeSettingsService;
    private final AdminConfigSnapshotService adminConfigSnapshotService;

    @Transactional
    public AdminSettingsResponse updateSettings(AdminSettingsUpdateRequest request) {
        String normalizedInviteCode = normalizeQuery(request.registration().currentInviteCode());
        String currentInviteCode = registrationInviteService.updateCurrentInviteCode(normalizedInviteCode);

        AdminSettingsUpdateRequest normalizedRequest = new AdminSettingsUpdateRequest(
                request.site(),
                new AdminSettingsUpdateRequest.RegistrationSection(
                        request.registration().inviteCodeRequired(),
                        currentInviteCode,
                        request.registration().managementRoles()
                ),
                request.userSession(),
                request.transfer(),
                request.mediaProcessing(),
                request.queue(),
                request.appearance(),
                request.server()
        );
        adminRuntimeSettingsService.update(normalizedRequest);
        adminMetricsService.updateOfflineTransferStorageLimit(request.transfer().offlineTransferStorageLimitBytes());

        adminAuditService.record(
                AdminAuditAction.UPDATE_SYSTEM_SETTINGS,
                "SYSTEM_SETTING",
                null,
                "Updated admin settings snapshot",
                Map.ofEntries(
                        Map.entry("siteSupported", request.site().supported()),
                        Map.entry("inviteCodeRequired", request.registration().inviteCodeRequired()),
                        Map.entry("managementRoleCount", request.registration().managementRoles().size()),
                        Map.entry("accessExpirationSeconds", request.userSession().accessExpirationSeconds()),
                        Map.entry("refreshExpirationSeconds", request.userSession().refreshExpirationSeconds()),
                        Map.entry("tokenBlacklistEnabled", request.userSession().tokenBlacklistEnabled()),
                        Map.entry("tokenBlacklistTtlBufferSeconds", request.userSession().tokenBlacklistTtlBufferSeconds()),
                        Map.entry("offlineTransferStorageLimitBytes", request.transfer().offlineTransferStorageLimitBytes()),
                        Map.entry("queueBackend", request.queue().backend()),
                        Map.entry("serverStorageProvider", request.server().storageProvider()),
                        Map.entry("serverRedisEnabled", request.server().redisEnabled()),
                        Map.entry("appearanceSupported", request.appearance().supported())
                )
        );
        return adminConfigSnapshotService.getSettings();
    }

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
