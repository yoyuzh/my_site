package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.config.AppRedisProperties;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminConfigSnapshotService {

    private final RegistrationInviteService registrationInviteService;
    private final AdminMetricsService adminMetricsService;
    private final AppRedisProperties redisProperties;
    private final FileStorageProperties fileStorageProperties;
    private final AdminRuntimeSettingsService adminRuntimeSettingsService;
    private final StoragePolicyQuery storagePolicyQuery;
    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final FileEntityRepository fileEntityRepository;

    public AdminSettingsResponse getSettings() {
        AdminRuntimeSettingsService.State state = adminRuntimeSettingsService.snapshot();
        boolean registrationWriteSupported = true;
        boolean transferWriteSupported = true;
        return new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(state.siteSupported(), false),
                new AdminSettingsResponse.RegistrationSection(
                        state.registrationInviteCodeRequired(),
                        registrationInviteService.getCurrentInviteCode(),
                        state.registrationManagementRoles().isEmpty()
                                ? java.util.List.of(UserRole.MODERATOR.name(), UserRole.ADMIN.name())
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
                        storedFileRepository.count(),
                        fileBlobRepository.count(),
                        fileEntityRepository.count()
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
