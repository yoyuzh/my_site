package com.yoyuzh.platform.storage.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.platform.storage.api.StorageUploadMode;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.api.UploadModePolicy;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.platform.storage.internal.infra.StoragePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoragePolicyServiceTest {

    @Mock
    private StoragePolicyRepository storagePolicyRepository;
    @Mock
    private UploadModePolicy uploadModePolicy;
    @Mock
    private UploadConstraintPolicy uploadConstraintPolicy;

    private FileStorageProperties properties;
    private StoragePolicyService storagePolicyService;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        storagePolicyService = new StoragePolicyService(
                storagePolicyRepository,
                properties,
                uploadModePolicy,
                uploadConstraintPolicy,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCreateDefaultS3CompatiblePolicyFromCurrentStorageConfig() {
        properties.setProvider("s3");
        properties.setMaxFileSize(5000L);
        properties.getS3().setScope("media-bucket:portal-prefix");
        properties.getS3().setRegion("automatic");
        when(storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()).thenReturn(Optional.empty());
        when(storagePolicyRepository.save(any(StoragePolicy.class))).thenAnswer(invocation -> {
            StoragePolicy policy = invocation.getArgument(0);
            policy.setId(1L);
            return policy;
        });

        StoragePolicy policy = storagePolicyService.ensureDefaultPolicy();

        assertThat(policy.getName()).isEqualTo("Default S3 Compatible Storage");
        assertThat(policy.getType()).isEqualTo(StoragePolicyType.S3_COMPATIBLE);
        assertThat(policy.getCredentialMode()).isEqualTo(StoragePolicyCredentialMode.DOGECLOUD_TEMP);
        assertThat(policy.getBucketName()).isEqualTo("media-bucket");
        assertThat(policy.getPrefix()).isEqualTo("portal-prefix");
        assertThat(policy.getRegion()).isEqualTo("automatic");
        assertThat(policy.isDefaultPolicy()).isTrue();
        assertThat(policy.isEnabled()).isTrue();

        StoragePolicyCapabilities capabilities = storagePolicyService.readCapabilities(policy);
        assertThat(capabilities.directUpload()).isTrue();
        assertThat(capabilities.multipartUpload()).isTrue();
        assertThat(capabilities.signedDownloadUrl()).isTrue();
        assertThat(capabilities.serverProxyDownload()).isTrue();
        assertThat(capabilities.requiresCors()).isTrue();
        assertThat(capabilities.maxObjectSize()).isEqualTo(5000L);
    }

    @Test
    void shouldCreateDefaultLocalPolicyFromCurrentStorageConfig() {
        properties.setProvider("local");
        properties.setMaxFileSize(2048L);
        properties.getLocal().setRootDir("./storage");
        when(storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()).thenReturn(Optional.empty());
        when(storagePolicyRepository.save(any(StoragePolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoragePolicy policy = storagePolicyService.ensureDefaultPolicy();

        assertThat(policy.getName()).isEqualTo("Default Local Storage");
        assertThat(policy.getType()).isEqualTo(StoragePolicyType.LOCAL);
        assertThat(policy.getCredentialMode()).isEqualTo(StoragePolicyCredentialMode.NONE);
        assertThat(policy.getPrefix()).isEqualTo("./storage");

        StoragePolicyCapabilities capabilities = storagePolicyService.readCapabilities(policy);
        assertThat(capabilities.directUpload()).isFalse();
        assertThat(capabilities.multipartUpload()).isFalse();
        assertThat(capabilities.signedDownloadUrl()).isFalse();
        assertThat(capabilities.serverProxyDownload()).isTrue();
        assertThat(capabilities.requiresCors()).isFalse();
        assertThat(capabilities.maxObjectSize()).isEqualTo(2048L);
    }

    @Test
    void shouldReuseExistingDefaultPolicy() {
        StoragePolicy existingPolicy = new StoragePolicy();
        existingPolicy.setId(7L);
        existingPolicy.setDefaultPolicy(true);
        existingPolicy.setEnabled(true);
        when(storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()).thenReturn(Optional.of(existingPolicy));

        StoragePolicy policy = storagePolicyService.ensureDefaultPolicy();

        assertThat(policy).isSameAs(existingPolicy);
        verify(storagePolicyRepository, never()).save(any(StoragePolicy.class));
    }

    @Test
    void shouldReadDefaultPolicySnapshot() {
        StoragePolicy existingPolicy = new StoragePolicy();
        existingPolicy.setId(7L);
        existingPolicy.setCapabilitiesJson(storagePolicyService.writeCapabilities(new StoragePolicyCapabilities(
                true,
                false,
                false,
                true,
                false,
                true,
                true,
                false,
                4096L
        )));
        when(storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()).thenReturn(Optional.of(existingPolicy));

        DefaultStoragePolicySnapshot snapshot = storagePolicyService.readDefaultPolicySnapshot();

        assertThat(snapshot.policyId()).isEqualTo(existingPolicy.getId());
        assertThat(snapshot.policyMaxSizeBytes()).isEqualTo(existingPolicy.getMaxSizeBytes());
        assertThat(snapshot.capabilities().maxObjectSize()).isEqualTo(4096L);
        assertThat(storagePolicyService.readDefaultPolicyId()).isEqualTo(7L);
    }

    @Test
    void shouldDelegateUploadModeResolution() {
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                4096L
        );
        when(uploadModePolicy.resolveUploadMode(any(com.yoyuzh.platform.storage.api.StoragePolicyCapabilities.class)))
                .thenReturn(StorageUploadMode.DIRECT_MULTIPART);

        StorageUploadMode uploadMode = storagePolicyService.resolveUploadMode(capabilities);

        assertThat(uploadMode).isEqualTo(StorageUploadMode.DIRECT_MULTIPART);
        verify(uploadModePolicy).resolveUploadMode(any(com.yoyuzh.platform.storage.api.StoragePolicyCapabilities.class));
    }

    @Test
    void shouldDelegateEffectiveMaxUploadSizeResolution() {
        StoragePolicy policy = new StoragePolicy();
        policy.setMaxSizeBytes(5_000L);
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true, true, true, true, false, true, true, false, 4096L
        );
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(10_000L, 6_000L, 5_000L, 4096L)).thenReturn(4096L);

        long effectiveMax = storagePolicyService.resolveEffectiveMaxUploadSize(
                10_000L,
                6_000L,
                policy.getMaxSizeBytes(),
                capabilities
        );

        assertThat(effectiveMax).isEqualTo(4096L);
        verify(uploadConstraintPolicy).resolveEffectiveMaxUploadSize(10_000L, 6_000L, 5_000L, 4096L);
    }
}
