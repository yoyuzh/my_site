package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.config.AppRedisProperties;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.config.JwtProperties;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyCredentialMode;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.policy.StoragePolicyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConfigSnapshotServiceTest {

    @Mock
    private RegistrationInviteService registrationInviteService;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private StoragePolicyService storagePolicyService;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileEntityRepository fileEntityRepository;

    private AppRedisProperties redisProperties;
    private FileStorageProperties fileStorageProperties;
    private JwtProperties jwtProperties;
    private MockEnvironment environment;
    private AdminConfigSnapshotService adminConfigSnapshotService;

    @BeforeEach
    void setUp() {
        redisProperties = new AppRedisProperties();
        fileStorageProperties = new FileStorageProperties();
        jwtProperties = new JwtProperties();
        environment = new MockEnvironment();
        adminConfigSnapshotService = new AdminConfigSnapshotService(
                registrationInviteService,
                adminMetricsService,
                redisProperties,
                fileStorageProperties,
                jwtProperties,
                environment,
                storagePolicyService,
                storedFileRepository,
                fileBlobRepository,
                fileEntityRepository
        );
    }

    @Test
    void shouldExposeAdminSettingsSnapshot() {
        redisProperties.setEnabled(true);
        redisProperties.setTtlBufferSeconds(120);
        jwtProperties.setAccessExpirationSeconds(1800);
        jwtProperties.setRefreshExpirationSeconds(604800);
        fileStorageProperties.setProvider("s3");
        environment.setProperty("app.redis.broker.media-meta.fixed-delay-ms", "5000");
        environment.setProperty("app.redis.broker.media-meta.initial-delay-ms", "25000");
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-2026");
        when(adminMetricsService.getOfflineTransferStorageLimitBytes()).thenReturn(20L * 1024 * 1024 * 1024);

        AdminSettingsResponse response = adminConfigSnapshotService.getSettings();

        assertThat(response.site().supported()).isFalse();
        assertThat(response.registration().inviteCodeRequired()).isTrue();
        assertThat(response.registration().currentInviteCode()).isEqualTo("INV-2026");
        assertThat(response.registration().managementRoles()).containsExactly("MODERATOR", "ADMIN");
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

        StoragePolicy policy = createStoragePolicy(7L, "Default S3 Storage");
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setDefaultPolicy(true);
        policy.setMaxSizeBytes(400_000L);
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
        when(storagePolicyService.ensureDefaultPolicy()).thenReturn(policy);
        when(storagePolicyService.readCapabilities(policy)).thenReturn(capabilities);
        when(storedFileRepository.count()).thenReturn(12L);
        when(fileBlobRepository.count()).thenReturn(8L);
        when(fileEntityRepository.count()).thenReturn(9L);

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

    private StoragePolicy createStoragePolicy(Long id, String name) {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(id);
        policy.setName(name);
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setBucketName("bucket");
        policy.setEndpoint("https://s3.example.com");
        policy.setRegion("auto");
        policy.setPrivateBucket(true);
        policy.setPrefix("files/");
        policy.setCredentialMode(StoragePolicyCredentialMode.STATIC);
        policy.setMaxSizeBytes(10_240L);
        policy.setCapabilitiesJson("{}");
        policy.setEnabled(true);
        policy.setDefaultPolicy(false);
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }
}
