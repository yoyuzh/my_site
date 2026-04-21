package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.transfer.OfflineTransferService;
import com.yoyuzh.transfer.api.TransferImportCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private FileBlobRepository fileBlobRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private StoragePolicyQuery storagePolicyQuery;
    @Mock
    private UploadConstraintPolicy uploadConstraintPolicy;
    @Mock
    private UserRepository userRepository;

    private RuntimeTransferImportApi transferImportApi;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L);
        transferImportApi = new RuntimeTransferImportApi(
                offlineTransferService,
                workspacePathPolicy,
                contentRegistrationApi,
                fileBlobRepository,
                storedFileRepository,
                fileContentStorage,
                storagePolicyQuery,
                uploadConstraintPolicy,
                userRepository,
                properties
        );
    }

    @Test
    void shouldImportOfflineFileThroughWorkspaceAndContentSeams() {
        User recipient = createUser(7L);
        OfflineTransferService.ReadyOfflineTransferFile readyFile = new OfflineTransferService.ReadyOfflineTransferFile(
                "offline.txt",
                "text/plain",
                12L,
                "hello offline".getBytes()
        );
        when(offlineTransferService.readReadyFile("session-1", "file-1")).thenReturn(readyFile);
        when(workspacePathPolicy.normalizeDirectoryPath("/docs")).thenReturn("/docs");
        when(workspacePathPolicy.normalizeLeafName("offline.txt")).thenReturn("offline.txt");
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(recipient));
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(defaultPolicySnapshot());
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(eq(500L), eq(500L), eq(0L), eq(500L))).thenReturn(500L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(10L);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        verify(fileContentStorage).storeBlob(any(), eq("text/plain"), eq("hello offline".getBytes()));
        verify(contentRegistrationApi).registerBlob(any(ContentRegistrationCommand.class));
    }

    @Test
    void shouldDeleteBlobWhenContentRegistrationFails() {
        User recipient = createUser(7L);
        OfflineTransferService.ReadyOfflineTransferFile readyFile = new OfflineTransferService.ReadyOfflineTransferFile(
                "offline.txt",
                "text/plain",
                12L,
                "hello offline".getBytes()
        );
        when(offlineTransferService.readReadyFile("session-1", "file-1")).thenReturn(readyFile);
        when(workspacePathPolicy.normalizeDirectoryPath("/docs")).thenReturn("/docs");
        when(workspacePathPolicy.normalizeLeafName("offline.txt")).thenReturn("offline.txt");
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(recipient));
        when(storagePolicyQuery.readDefaultPolicySnapshot()).thenReturn(defaultPolicySnapshot());
        when(uploadConstraintPolicy.resolveEffectiveMaxUploadSize(eq(500L), eq(500L), eq(0L), eq(500L))).thenReturn(500L);
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(10L);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
