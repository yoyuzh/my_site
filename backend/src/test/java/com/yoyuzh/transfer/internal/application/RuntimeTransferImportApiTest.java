package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityStorageUsageQuery;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StorageRuntimeLimitApi;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.transfer.api.TransferImportCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTransferImportApiTest {

    @Mock
    private OfflineTransferService offlineTransferService;
    @Mock
    private WorkspacePathPolicy workspacePathPolicy;
    @Mock
    private ContentRegistrationApi contentRegistrationApi;
    @Mock
    private ContentBlobRegistrationApi contentBlobRegistrationApi;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private StoragePolicyQuery storagePolicyQuery;
    @Mock
    private UploadConstraintPolicy uploadConstraintPolicy;
    @Mock
    private StorageRuntimeLimitApi storageRuntimeLimitApi;
    @Mock
    private IdentityUserDirectoryApi identityUserDirectoryApi;
    @Mock
    private IdentityStorageUsageQuery identityStorageUsageQuery;

    private RuntimeTransferImportApi transferImportApi;

    @BeforeEach
    void setUp() {
        transferImportApi = new RuntimeTransferImportApi(
                offlineTransferService,
                workspacePathPolicy,
                contentRegistrationApi,
                contentBlobRegistrationApi,
                fileContentStorage,
                storagePolicyQuery,
                uploadConstraintPolicy,
                storageRuntimeLimitApi,
                identityUserDirectoryApi,
                identityStorageUsageQuery
        );
    }

    @Test
    void shouldImportOfflineFileThroughWorkspaceAndContentSeams() {
        User recipient = createUser(7L);
        OfflineTransferService.ReadyOfflineTransferFile readyFile = new OfflineTransferService.ReadyOfflineTransferFile(
                "offline.txt",
                "text/plain",
                12L,
                () -> new ByteArrayInputStream("hello offline".getBytes())
        );
        when(offlineTransferService.readReadyFile("session-1", "file-1")).thenReturn(readyFile);
        when(workspacePathPolicy.normalizeDirectoryPath("/docs")).thenReturn("/docs");
        when(workspacePathPolicy.normalizeLeafName("offline.txt")).thenReturn("offline.txt");
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(recipient)));
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(defaultPolicySnapshot());
        when(storageRuntimeLimitApi.maxFileSizeBytes()).thenReturn(500L);
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(eq(500L), eq(500L), eq(0L), eq(500L))).thenReturn(500L);
        when(identityStorageUsageQuery.usedStorageBytes(7L)).thenReturn(10L);
        when(contentBlobRegistrationApi.registerStoredBlob(any(), eq("text/plain"), eq(12L)))
                .thenReturn(new ContentBlobReference(100L, "blob-key", "text/plain", 12L));
        when(contentRegistrationApi.registerBlob(any(ContentRegistrationCommand.class))).thenReturn(new RegisteredContentFile(
                21L,
                "offline.txt",
                "/docs",
                12L,
                "text/plain",
                false,
                LocalDateTime.now()
        ));

        FileMetadataResponse response = transferImportApi.importOfflineFile(
                recipient.getId(),
                "session-1",
                "file-1",
                new TransferImportCommand("/docs")
        );

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.path()).isEqualTo("/docs");
        verify(workspacePathPolicy).ensureNodeNameAvailable(7L, "/docs", "offline.txt", "同目录下文件已存在");
        verify(workspacePathPolicy).ensureDirectoryHierarchy(7L, "/docs");
        verify(fileContentStorage).storeBlob(any(), eq("text/plain"), any(InputStream.class), eq(12L));
        verify(contentRegistrationApi).registerBlob(any(ContentRegistrationCommand.class));
    }

    @Test
    void shouldDeleteBlobWhenContentRegistrationFails() {
        User recipient = createUser(7L);
        OfflineTransferService.ReadyOfflineTransferFile readyFile = new OfflineTransferService.ReadyOfflineTransferFile(
                "offline.txt",
                "text/plain",
                12L,
                () -> new ByteArrayInputStream("hello offline".getBytes())
        );
        when(offlineTransferService.readReadyFile("session-1", "file-1")).thenReturn(readyFile);
        when(workspacePathPolicy.normalizeDirectoryPath("/docs")).thenReturn("/docs");
        when(workspacePathPolicy.normalizeLeafName("offline.txt")).thenReturn("offline.txt");
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(recipient)));
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(defaultPolicySnapshot());
        when(storageRuntimeLimitApi.maxFileSizeBytes()).thenReturn(500L);
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(eq(500L), eq(500L), eq(0L), eq(500L))).thenReturn(500L);
        when(identityStorageUsageQuery.usedStorageBytes(7L)).thenReturn(10L);
        when(contentBlobRegistrationApi.registerStoredBlob(any(), eq("text/plain"), eq(12L)))
                .thenReturn(new ContentBlobReference(100L, "blob-key", "text/plain", 12L));
        when(contentRegistrationApi.registerBlob(any(ContentRegistrationCommand.class))).thenThrow(new IllegalStateException("registration failed"));

        assertThatThrownBy(() -> transferImportApi.importOfflineFile(
                recipient.getId(),
                "session-1",
                "file-1",
                new TransferImportCommand("/docs")
        )).isInstanceOf(IllegalStateException.class);

        verify(fileContentStorage).deleteBlob(any());
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setStorageQuotaBytes(500L);
        user.setMaxUploadSizeBytes(500L);
        return user;
    }

    private IdentityUserSnapshot snapshot(User user) {
        return new IdentityUserSnapshot(
                user.getId(),
                user.getUsername(),
                user.getUsername(),
                user.getUsername() + "@example.com",
                null,
                null,
                "zh-CN",
                null,
                null,
                null,
                IdentityRoleName.USER,
                LocalDateTime.now(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    private DefaultStoragePolicySnapshot defaultPolicySnapshot() {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(42L);
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setEnabled(true);
        policy.setMaxSizeBytes(0L);
        return new DefaultStoragePolicySnapshot(
                policy.getId(),
                policy.getMaxSizeBytes(),
                new StoragePolicyCapabilities(true, false, true, true, false, true, true, false, 500L)
        );
    }
}
