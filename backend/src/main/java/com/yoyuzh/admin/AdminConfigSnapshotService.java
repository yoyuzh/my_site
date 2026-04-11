package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.config.AppRedisProperties;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.config.JwtProperties;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminConfigSnapshotService {

    private final RegistrationInviteService registrationInviteService;
    private final AdminMetricsService adminMetricsService;
    private final AppRedisProperties redisProperties;
    private final FileStorageProperties fileStorageProperties;
    private final JwtProperties jwtProperties;
    private final Environment environment;
    private final StoragePolicyService storagePolicyService;
    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final FileEntityRepository fileEntityRepository;

    public AdminSettingsResponse getSettings() {
        return new AdminSettingsResponse(
                new AdminSettingsResponse.SiteSection(false, false),
                new AdminSettingsResponse.RegistrationSection(
                        true,
                        registrationInviteService.getCurrentInviteCode(),
                        List.of(UserRole.MODERATOR.name(), UserRole.ADMIN.name()),
                        true
                ),
                new AdminSettingsResponse.UserSessionSection(
                        jwtProperties.getAccessExpirationSeconds(),
                        jwtProperties.getRefreshExpirationSeconds(),
                        redisProperties.isEnabled(),
                        redisProperties.getTtlBufferSeconds(),
                        false
                ),
                new AdminSettingsResponse.TransferSection(
                        adminMetricsService.getOfflineTransferStorageLimitBytes(),
                        true
                ),
                new AdminSettingsResponse.MediaProcessingSection(true, false, false, false),
                new AdminSettingsResponse.QueueSection(
                        redisProperties.isEnabled() ? "redis" : "in-memory",
                        readLongProperty("app.redis.broker.media-meta.fixed-delay-ms", 3000L),
                        readLongProperty("app.redis.broker.media-meta.initial-delay-ms", 15000L),
                        false
                ),
                new AdminSettingsResponse.AppearanceSection(false, false),
                new AdminSettingsResponse.ServerSection(
                        normalizeStorageProvider(fileStorageProperties.getProvider()),
                        redisProperties.isEnabled(),
                        false
                )
        );
    }

    public AdminFilesystemResponse getFilesystem() {
        StoragePolicy defaultPolicy = storagePolicyService.ensureDefaultPolicy();
        StoragePolicyCapabilities capabilities = storagePolicyService.readCapabilities(defaultPolicy);
        boolean directUpload = capabilities.directUpload();
        return new AdminFilesystemResponse(
                new AdminFilesystemResponse.OverviewSection(
                        normalizeStorageProvider(fileStorageProperties.getProvider()),
                        storedFileRepository.count(),
                        fileBlobRepository.count(),
                        fileEntityRepository.count()
                ),
                AdminStoragePolicyResponses.from(storagePolicyService, defaultPolicy),
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

    private long readLongProperty(String key, long defaultValue) {
        return environment.getProperty(key, Long.class, defaultValue);
    }
}
