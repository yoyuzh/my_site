package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.content.api.ContentAdminInspectionApi;
import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminConfigSnapshotService {

    private final IdentityAdminSummaryApi identityAdminSummaryApi;
    private final AdminMetricsService adminMetricsService;
    private final AppRedisProperties redisProperties;
    private final FileStorageProperties fileStorageProperties;
    private final AdminRuntimeSettingsService adminRuntimeSettingsService;
    private final StoragePolicyQuery storagePolicyQuery;
    private final WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    private final ContentAdminInspectionApi contentAdminInspectionApi;

    public AdminSettingsResponse getSettings() {
        AdminRuntimeSettingsService.State state = adminRuntimeSettingsService.snapshot();
        boolean registrationWriteSupported = true;
        boolean transferWriteSupported = true;
        return new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(state.siteSupported(), false),
                new AdminSettingsResponse.RegistrationSection(
                        state.registrationInviteCodeRequired(),
                        identityAdminSummaryApi.currentInviteCode(),
                        state.registrationManagementRoles().isEmpty()
                                ? java.util.List.of(IdentityRoleName.MODERATOR.name(), IdentityRoleName.ADMIN.name())
                                : state.registrationManagementRoles(),
                        registrationWriteSupported
                ),
                new AdminSettingsResponse.UserSessionSection(
                        state.userSessionAccessExpirationSeconds(),
                        state.userSessionRefreshExpirationSeconds(),
                        state.userSessionTokenBlacklistEnabled(),
                        state.userSessionTokenBlacklistTtlBufferSeconds(),
                        false
                ),
                new AdminSettingsResponse.TransferSection(
                        adminMetricsService.getOfflineTransferStorageLimitBytes(),
                        transferWriteSupported
                ),
                new AdminSettingsResponse.MediaProcessingSection(
                        state.mediaMetadataExtractionEnabled(),
                        state.mediaThumbnailGenerationEnabled(),
                        state.mediaVideoPosterEnabled(),
                        false
                ),
                new AdminSettingsResponse.QueueSection(
                        state.queueBackend(),
                        state.queueMediaMetadataFixedDelayMs(),
                        state.queueMediaMetadataInitialDelayMs(),
                        false
                ),
                new AdminSettingsResponse.AppearanceSection(state.appearanceSupported(), false),
                new AdminSettingsResponse.ServerSection(
                        state.serverStorageProvider(),
                        state.serverRedisEnabled(),
                        false
                )
        );
    }

    public AdminFilesystemResponse getFilesystem() {
        var defaultPolicySnapshot = storagePolicyQuery.readDefaultPolicySnapshot();
        StoragePolicy defaultPolicy = defaultPolicySnapshot.policy();
        StoragePolicyCapabilities capabilities = defaultPolicySnapshot.capabilities();
        boolean directUpload = capabilities.directUpload();
        return new AdminFilesystemResponse(
                new AdminFilesystemResponse.OverviewSection(
                        normalizeStorageProvider(fileStorageProperties.getProvider()),
                        workspaceAdminGovernanceApi.countFilesAsAdmin(),
                        contentAdminInspectionApi.countBlobsAsAdmin(),
                        contentAdminInspectionApi.countEntitiesAsAdmin()
                ),
                AdminStoragePolicyResponses.from(defaultPolicy, capabilities),
                new AdminFilesystemResponse.UploadSection(
                        !directUpload,
                        directUpload && !capabilities.multipartUpload(),
                        directUpload && capabilities.multipartUpload(),
                        resolveEffectiveMaxFileSize(defaultPolicy, capabilities)
                ),
                new AdminFilesystemResponse.MediaProcessingSection(true, capabilities.thumbnailNative()),
                new AdminFilesystemResponse.CacheSection(
                        redisProperties.isEnabled() ? "redis" : "disabled",
                        redisProperties.getCache().getFilesListTtlSeconds(),
                        redisProperties.getCache().getDirectoryVersionTtlSeconds()
                ),
                new AdminFilesystemResponse.WebdavSection(false)
        );
    }

    private String normalizeStorageProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "local";
        }
        return provider.trim().toLowerCase();
    }

    private long resolveEffectiveMaxFileSize(StoragePolicy policy, StoragePolicyCapabilities capabilities) {
        long effectiveMaxFileSize = fileStorageProperties.getMaxFileSize();
        if (policy.getMaxSizeBytes() > 0) {
            effectiveMaxFileSize = Math.min(effectiveMaxFileSize, policy.getMaxSizeBytes());
        }
        if (capabilities.maxObjectSize() > 0) {
            effectiveMaxFileSize = Math.min(effectiveMaxFileSize, capabilities.maxObjectSize());
        }
        return effectiveMaxFileSize;
    }

}
