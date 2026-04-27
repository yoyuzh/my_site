package com.yoyuzh.platform.storage.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationApi;
import com.yoyuzh.files.content.api.ContentStoragePolicyMigrationInspection;
import com.yoyuzh.platform.storage.api.StoragePolicyBlobAccessApi;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.internal.infra.StoragePolicyRepository;
import com.yoyuzh.platform.storage.internal.application.StoragePolicyService;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminUpsertCommand;
import com.yoyuzh.platform.storage.api.StoragePolicyAdminView;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode;
import com.yoyuzh.platform.storage.api.StoragePolicyMigrationCandidate;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeStoragePolicyAdminApiTest {

    @Mock
    private StoragePolicyRepository storagePolicyRepository;
    @Mock
    private StoragePolicyService storagePolicyService;
    @Mock
    private ContentStoragePolicyMigrationApi contentStoragePolicyMigrationApi;
    @Mock
    private StoragePolicyBlobAccessApi storagePolicyBlobAccessApi;

    private RuntimeStoragePolicyAdminApi runtimeStoragePolicyAdminApi;

    @BeforeEach
    void setUp() {
        runtimeStoragePolicyAdminApi = new RuntimeStoragePolicyAdminApi(
                storagePolicyRepository,
                storagePolicyService,
                contentStoragePolicyMigrationApi,
                storagePolicyBlobAccessApi
        );
    }

    @Test
    void shouldReadDefaultStoragePolicyAsAdmin() {
        StoragePolicy defaultPolicy = createPolicy(1L, "Default Local Storage");
        defaultPolicy.setType(com.yoyuzh.platform.storage.api.StoragePolicyType.LOCAL);
        defaultPolicy.setCredentialMode(com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode.NONE);
        var capabilities = new StoragePolicyCapabilities(
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                1024L
        );
        when(storagePolicyService.ensureDefaultPolicy()).thenReturn(defaultPolicy);
        when(storagePolicyService.readCapabilities(defaultPolicy)).thenReturn(capabilities);

        StoragePolicyAdminView response = runtimeStoragePolicyAdminApi.readDefaultStoragePolicyAsAdmin();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo(StoragePolicyType.LOCAL);
        assertThat(response.credentialMode()).isEqualTo(StoragePolicyCredentialMode.NONE);
        assertThat(response.capabilities().serverProxyDownload()).isTrue();
    }

    @Test
    void shouldCreateStoragePolicyAsAdminWithApiTypes() {
        StoragePolicy savedPolicy = createPolicy(2L, "Archive Bucket");
        savedPolicy.setType(com.yoyuzh.platform.storage.api.StoragePolicyType.S3_COMPATIBLE);
        savedPolicy.setCredentialMode(com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode.DOGECLOUD_TEMP);
        var capabilities = new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                40960L
        );

        when(storagePolicyService.writeCapabilities(any(com.yoyuzh.platform.storage.api.StoragePolicyCapabilities.class)))
                .thenReturn("{\"maxObjectSize\":40960}");
        when(storagePolicyRepository.save(any(StoragePolicy.class))).thenReturn(savedPolicy);
        when(storagePolicyService.readCapabilities(savedPolicy)).thenReturn(capabilities);

        StoragePolicyAdminView response = runtimeStoragePolicyAdminApi.createStoragePolicyAsAdmin(new StoragePolicyAdminUpsertCommand(
                "Archive Bucket",
                StoragePolicyType.S3_COMPATIBLE,
                "archive-bucket",
                "https://s3.example.com",
                "auto",
                true,
                "archive/",
                StoragePolicyCredentialMode.DOGECLOUD_TEMP,
                40960L,
                new StoragePolicyCapabilities(
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        true,
                        false,
                        40960L
                ),
                true
        ));

        assertThat(response.type()).isEqualTo(StoragePolicyType.S3_COMPATIBLE);
        assertThat(response.credentialMode()).isEqualTo(StoragePolicyCredentialMode.DOGECLOUD_TEMP);
        assertThat(response.capabilities().directUpload()).isTrue();

        ArgumentCaptor<StoragePolicyCapabilities> capabilitiesCaptor =
                ArgumentCaptor.forClass(StoragePolicyCapabilities.class);
        verify(storagePolicyService).writeCapabilities(capabilitiesCaptor.capture());
        assertThat(capabilitiesCaptor.getValue().maxObjectSize()).isEqualTo(40960L);
    }

    @Test
    void shouldBuildStoragePolicyMigrationCandidate() {
        StoragePolicy sourcePolicy = createPolicy(3L, "Source");
        StoragePolicy targetPolicy = createPolicy(4L, "Target");
        targetPolicy.setEnabled(true);
        when(storagePolicyRepository.findById(3L)).thenReturn(Optional.of(sourcePolicy));
        when(storagePolicyRepository.findById(4L)).thenReturn(Optional.of(targetPolicy));
        when(contentStoragePolicyMigrationApi.inspectVersionItemsByStoragePolicyId(3L))
                .thenReturn(new ContentStoragePolicyMigrationInspection(5L, 8L, "VERSION"));

        StoragePolicyMigrationCandidate candidate =
                runtimeStoragePolicyAdminApi.buildStoragePolicyMigrationCandidate(3L, 4L);

        assertThat(candidate.sourcePolicyId()).isEqualTo(3L);
        assertThat(candidate.targetPolicyId()).isEqualTo(4L);
        assertThat(candidate.candidateEntityCount()).isEqualTo(5L);
        assertThat(candidate.candidateStoredFileCount()).isEqualTo(8L);
        assertThat(candidate.entityType()).isEqualTo("VERSION");
    }

    @Test
    void shouldRejectRelativeLocalStorageRoot() {
        assertThatThrownBy(() -> runtimeStoragePolicyAdminApi.createStoragePolicyAsAdmin(new StoragePolicyAdminUpsertCommand(
                "Archive Local",
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                true,
                "archive",
                StoragePolicyCredentialMode.NONE,
                40960L,
                new StoragePolicyCapabilities(
                        false,
                        false,
                        false,
                        true,
                        false,
                        true,
                        false,
                        false,
                        40960L
                ),
                true
        )))
                .isInstanceOf(com.yoyuzh.shared.kernel.BusinessException.class)
                .hasMessageContaining("绝对路径根目录");
    }

    @Test
    void shouldAcceptAbsoluteLocalStorageRoot() {
        StoragePolicy savedPolicy = createPolicy(5L, "Archive Local");
        savedPolicy.setType(StoragePolicyType.LOCAL);
        savedPolicy.setBucketName(null);
        savedPolicy.setEndpoint(null);
        savedPolicy.setRegion(null);
        savedPolicy.setPrefix("/srv/my-site/archive");
        savedPolicy.setCredentialMode(StoragePolicyCredentialMode.NONE);
        var capabilities = new StoragePolicyCapabilities(
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                40960L
        );
        when(storagePolicyService.writeCapabilities(any(StoragePolicyCapabilities.class))).thenReturn("{\"maxObjectSize\":40960}");
        when(storagePolicyRepository.save(any(StoragePolicy.class))).thenReturn(savedPolicy);
        when(storagePolicyService.readCapabilities(savedPolicy)).thenReturn(capabilities);

        StoragePolicyAdminView response = runtimeStoragePolicyAdminApi.createStoragePolicyAsAdmin(new StoragePolicyAdminUpsertCommand(
                "Archive Local",
                StoragePolicyType.LOCAL,
                null,
                null,
                null,
                true,
                "/srv/my-site/archive",
                StoragePolicyCredentialMode.NONE,
                40960L,
                capabilities,
                true
        ));

        assertThat(response.type()).isEqualTo(StoragePolicyType.LOCAL);
        assertThat(response.prefix()).isEqualTo("/srv/my-site/archive");
    }

    @Test
    void shouldRejectMissingCapabilities() {
        assertThatThrownBy(() -> runtimeStoragePolicyAdminApi.createStoragePolicyAsAdmin(new StoragePolicyAdminUpsertCommand(
                "Archive Bucket",
                StoragePolicyType.S3_COMPATIBLE,
                "archive-bucket",
                "https://s3.example.com",
                "auto",
                true,
                "archive/",
                StoragePolicyCredentialMode.DOGECLOUD_TEMP,
                40960L,
                null,
                true
        )))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("storage policy capabilities");
    }

    @Test
    void shouldUseStoragePolicyNotFoundErrorCode() {
        when(storagePolicyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runtimeStoragePolicyAdminApi.updateStoragePolicyStatusAsAdmin(404L, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.STORAGE_POLICY_NOT_FOUND));
    }

    private StoragePolicy createPolicy(Long id, String name) {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(id);
        policy.setName(name);
        policy.setType(com.yoyuzh.platform.storage.api.StoragePolicyType.S3_COMPATIBLE);
        policy.setBucketName("bucket");
        policy.setEndpoint("https://s3.example.com");
        policy.setRegion("auto");
        policy.setPrivateBucket(true);
        policy.setPrefix("files/");
        policy.setCredentialMode(com.yoyuzh.platform.storage.api.StoragePolicyCredentialMode.STATIC);
        policy.setMaxSizeBytes(10_240L);
        policy.setCapabilitiesJson("{}");
        policy.setEnabled(true);
        policy.setDefaultPolicy(false);
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }
}
