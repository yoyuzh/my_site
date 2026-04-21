package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.content.api.ContentAdminInspectionApi;
import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminApi;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConfigSnapshotServiceTest {

    @Mock
    private IdentityAdminSummaryApi identityAdminSummaryApi;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private StoragePolicyAdminApi storagePolicyAdminApi;
    @Mock
    private WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    @Mock
    private ContentAdminInspectionApi contentAdminInspectionApi;
    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    private AppRedisProperties redisProperties;
    private FileStorageProperties fileStorageProperties;
    private AdminConfigSnapshotService adminConfigSnapshotService;

    @BeforeEach
    void setUp() {
        redisProperties = new AppRedisProperties();
        fileStorageProperties = new FileStorageProperties();
        adminConfigSnapshotService = new AdminConfigSnapshotService(
                identityAdminSummaryApi,
                adminMetricsService,
                redisProperties,
                fileStorageProperties,
                adminRuntimeSettingsService,
                storagePolicyAdminApi,
                workspaceAdminGovernanceApi,
                contentAdminInspectionApi
        );
    }

    @Test
    void shouldExposeAdminSettingsSnapshot() {
        AdminRuntimeSettingsService.State runtimeState = new AdminRuntimeSettingsService.State(
                true,
                false,
                java.util.List.of("ADMIN"),
                1800L,
                604800L,
                true,
                120L,
                true,
                true,
                false,
                "redis",
                5000L,
                25000L,
                true,
                "s3",
                true
        );
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState);
        when(identityAdminSummaryApi.currentInviteCode()).thenReturn("INV-2026");
        when(adminMetricsService.getOfflineTransferStorageLimitBytes()).thenReturn(20L * 1024 * 1024 * 1024);

        AdminSettingsResponse response = adminConfigSnapshotService.getSettings();

        assertThat(response.site().supported()).isTrue();
        assertThat(response.site().writeSupported()).isFalse();
        assertThat(response.registration().inviteCodeRequired()).isFalse();
        assertThat(response.registration().currentInviteCode()).isEqualTo("INV-2026");
        assertThat(response.registration().managementRoles()).containsExactly("ADMIN");
        assertThat(response.registration().writeSupported()).isTrue();
        assertThat(response.userSession().accessExpirationSeconds()).isEqualTo(1800L);
        assertThat(response.userSession().refreshExpirationSeconds()).isEqualTo(604800L);
        assertThat(response.userSession().tokenBlacklistEnabled()).isTrue();
        assertThat(response.userSession().writeSupported()).isFalse();
        assertThat(response.transfer().offlineTransferStorageLimitBytes()).isGreaterThan(0L);
        assertThat(response.transfer().writeSupported()).isTrue();
        assertThat(response.queue().backend()).isEqualTo("redis");
        assertThat(response.queue().writeSupported()).isFalse();
        assertThat(response.queue().mediaMetadataFixedDelayMs()).isEqualTo(5000L);
        assertThat(response.queue().mediaMetadataInitialDelayMs()).isEqualTo(25000L);
        assertThat(response.server().storageProvider()).isEqualTo("s3");
        assertThat(response.server().redisEnabled()).isTrue();
        assertThat(response.server().writeSupported()).isFalse();
    }

    @Test
    void shouldExposeFilesystemOverviewFromDefaultPolicy() {
        fileStorageProperties.setProvider("s3");
        fileStorageProperties.setMaxFileSize(500_000L);
        redisProperties.setEnabled(true);
        redisProperties.getCache().setFilesListTtlSeconds(45);
        redisProperties.getCache().setDirectoryVersionTtlSeconds(3600);

        StoragePolicyAdminView policy = createStoragePolicy(7L, "Default S3 Storage");
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                300_000L
        );
        StoragePolicyAdminView defaultPolicy = new StoragePolicyAdminView(
                policy.id(),
                policy.name(),
                policy.type(),
                policy.bucketName(),
                policy.endpoint(),
                policy.region(),
                policy.privateBucket(),
                policy.prefix(),
                policy.credentialMode(),
                400_000L,
                capabilities,
                policy.enabled(),
                true,
                policy.createdAt(),
                policy.updatedAt()
        );
        when(storagePolicyAdminApi.readDefaultStoragePolicyAsAdmin()).thenReturn(defaultPolicy);
        when(workspaceAdminGovernanceApi.countFilesAsAdmin()).thenReturn(12L);
        when(contentAdminInspectionApi.countBlobsAsAdmin()).thenReturn(8L);
        when(contentAdminInspectionApi.countEntitiesAsAdmin()).thenReturn(9L);

        AdminFilesystemResponse response = adminConfigSnapshotService.getFilesystem();

        assertThat(response.overview().storageProvider()).isEqualTo("s3");
        assertThat(response.overview().totalFiles()).isEqualTo(12L);
        assertThat(response.overview().totalBlobs()).isEqualTo(8L);
        assertThat(response.overview().totalEntities()).isEqualTo(9L);
        assertThat(response.defaultPolicy().id()).isEqualTo(7L);
        assertThat(response.upload().proxyUpload()).isFalse();
        assertThat(response.upload().directSingleUpload()).isFalse();
        assertThat(response.upload().directMultipartUpload()).isTrue();
        assertThat(response.upload().effectiveMaxFileSizeBytes()).isEqualTo(300_000L);
        assertThat(response.mediaProcessing().metadataExtractionEnabled()).isTrue();
        assertThat(response.mediaProcessing().nativeThumbnailSupport()).isFalse();
        assertThat(response.cache().backend()).isEqualTo("redis");
        assertThat(response.cache().filesListTtlSeconds()).isEqualTo(45L);
        assertThat(response.cache().directoryVersionTtlSeconds()).isEqualTo(3600L);
        assertThat(response.webdav().enabled()).isFalse();
    }

    private StoragePolicyAdminView createStoragePolicy(Long id, String name) {
        return new StoragePolicyAdminView(
                id,
                name,
                StoragePolicyType.S3_COMPATIBLE,
                "bucket",
                "https://s3.example.com",
                "auto",
                true,
                "files/",
                StoragePolicyCredentialMode.STATIC,
                10_240L,
                new StoragePolicyCapabilities(
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        true,
                        false,
                        10_240L
                ),
                true,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
