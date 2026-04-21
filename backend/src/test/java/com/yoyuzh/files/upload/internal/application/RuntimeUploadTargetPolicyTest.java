package com.yoyuzh.files.upload.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.internal.application.RuntimeUploadConstraintPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeUploadTargetPolicyTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private WorkspacePathPolicy workspacePathPolicy;
    @Mock
    private StoragePolicyQuery storagePolicyQuery;

    private RuntimeUploadTargetPolicy uploadTargetPolicy;

    @BeforeEach
    void setUp() {
        uploadTargetPolicy = new RuntimeUploadTargetPolicy(
                storedFileRepository,
                workspacePathPolicy,
                storagePolicyQuery,
                new RuntimeUploadConstraintPolicy(),
                500L * 1024 * 1024
        );
    }

    @Test
    void shouldNormalizeValidateAndReturnDefaultPolicySnapshot() {
        User user = createUser(7L);
        StoragePolicy policy = createDefaultStoragePolicy();
        DefaultStoragePolicySnapshot snapshot = new DefaultStoragePolicySnapshot(
                policy.getId(),
                policy.getMaxSizeBytes(),
                new StoragePolicyCapabilities(true, true, true, true, false, true, true, false, 300L)
        );
        when(workspacePathPolicy.normalizeDirectoryPath("/docs")).thenReturn("/docs");
        when(workspacePathPolicy.normalizeLeafName("movie.mp4")).thenReturn("movie.mp4");
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(snapshot);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(10L);

        ValidatedUploadTarget target = uploadTargetPolicy.validateUpload(
                user.getId(),
                user.getMaxUploadSizeBytes(),
                user.getStorageQuotaBytes(),
                "/docs",
                "movie.mp4",
                120L
        );

        assertThat(target.normalizedPath()).isEqualTo("/docs");
        assertThat(target.filename()).isEqualTo("movie.mp4");
        assertThat(target.defaultPolicySnapshot()).isSameAs(snapshot);
    }

    @Test
    void shouldRejectUploadWhenQuotaWouldOverflow() {
        User user = createUser(7L);
        user.setStorageQuotaBytes(100L);
        StoragePolicy policy = createDefaultStoragePolicy();
        when(workspacePathPolicy.normalizeDirectoryPath("/docs")).thenReturn("/docs");
        when(workspacePathPolicy.normalizeLeafName("movie.mp4")).thenReturn("movie.mp4");
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(new DefaultStoragePolicySnapshot(
                policy.getId(),
                policy.getMaxSizeBytes(),
                new StoragePolicyCapabilities(true, false, true, true, false, true, true, false, 500L)
        ));
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(90L);

        assertThatThrownBy(() -> uploadTargetPolicy.validateUpload(
                user.getId(),
                user.getMaxUploadSizeBytes(),
                user.getStorageQuotaBytes(),
                "/docs",
                "movie.mp4",
                20L
        ))
                .isInstanceOf(BusinessException.class);
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        user.setPasswordHash("encoded");
        user.setMaxUploadSizeBytes(500L * 1024 * 1024);
        user.setStorageQuotaBytes(500L * 1024 * 1024);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoragePolicy createDefaultStoragePolicy() {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(42L);
        policy.setName("default");
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setEnabled(true);
        policy.setDefaultPolicy(true);
        policy.setMaxSizeBytes(500L * 1024 * 1024);
        return policy;
    }
}
