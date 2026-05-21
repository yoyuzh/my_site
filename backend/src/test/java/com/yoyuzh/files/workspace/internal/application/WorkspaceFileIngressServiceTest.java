package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.internal.application.ContentBlobLifecycleService;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class WorkspaceFileIngressServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private ContentAssetApi contentAssetApi;
    @Mock
    private ContentBlobQueryApi contentBlobQueryApi;
    @Mock
    private ContentRegistrationApi contentRegistrationApi;
    @Mock
    private ContentBlobRegistrationApi contentBlobRegistrationApi;
    @Mock
    private UploadCompletionApi uploadCompletionApi;
    @Mock
    private com.yoyuzh.files.content.api.ContentBlobLifecycleApi contentBlobLifecycleApi;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private StorageRuntimeProperties storageRuntimeProperties;

    @Test
    void shouldDeleteTempFileAndMarkBlobFailedWhenDeferredCreateRegistrationFails() throws Exception {
        WorkspaceFileIngressService service = createService();
        WorkspaceUserContext user = new WorkspaceUserContext(7L, 1024L, 1024L);
        ContentBlobReference blob = new ContentBlobReference(11L, "blobs/pending-create", "text/plain", 5L);
        when(storedFileRepository.findActiveNodesByUserIdAndPathInAndFilenameIn(eq(7L), any(), any()))
                .thenReturn(java.util.List.of());
        when(contentBlobRegistrationApi.registerPendingBlob(anyString(), eq("text/plain"), eq(5L), anyString()))
                .thenReturn(blob);
        when(contentRegistrationApi.registerBlob(any()))
                .thenThrow(new IllegalStateException("register failed"));

        ArgumentCaptor<String> tempPathCaptor = ArgumentCaptor.forClass(String.class);

        assertThatThrownBy(() -> service.prepareDeferredCreate(
                user,
                "/Docs",
                "a.txt",
                "text/plain",
                5L,
                new ByteArrayInputStream("hello".getBytes(UTF_8))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("register failed");

        verify(contentBlobRegistrationApi).registerPendingBlob(anyString(), eq("text/plain"), eq(5L), tempPathCaptor.capture());
        verify(contentBlobRegistrationApi).markBlobFailed(11L);
        assertThat(Files.exists(Path.of(tempPathCaptor.getValue()))).isFalse();
    }

    @Test
    void shouldDeleteTempFileAndMarkBlobFailedWhenDeferredReplaceMetadataLookupFails() throws Exception {
        WorkspaceFileIngressService service = createService();
        WorkspaceUserContext user = new WorkspaceUserContext(7L, 1024L, 1024L);
        ContentBlobReference blob = new ContentBlobReference(12L, "blobs/pending-replace", "text/plain", 5L);
        StoredFile existing = storedFile(22L, 7L, "/Docs", "a.txt", 90L, 91L);
        when(contentBlobRegistrationApi.registerPendingBlob(anyString(), eq("text/plain"), eq(5L), anyString()))
                .thenReturn(blob);
        when(storedFileRepository.findDetailedByIdAndUserId(22L, 7L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing))
                .thenThrow(new IllegalStateException("lookup failed"));

        ArgumentCaptor<String> tempPathCaptor = ArgumentCaptor.forClass(String.class);

        assertThatThrownBy(() -> service.prepareDeferredReplace(
                user,
                22L,
                "text/plain",
                5L,
                3L,
                new ByteArrayInputStream("world".getBytes(UTF_8))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("lookup failed");

        verify(contentBlobRegistrationApi).registerPendingBlob(anyString(), eq("text/plain"), eq(5L), tempPathCaptor.capture());
        verify(contentBlobRegistrationApi).markBlobFailed(12L);
        assertThat(Files.exists(Path.of(tempPathCaptor.getValue()))).isFalse();
    }

    @Test
    void shouldDeleteTempFileAfterRollbackWhenDeferredCreateWasAlreadyStaged() throws Exception {
        WorkspaceFileIngressService service = createService();
        WorkspaceUserContext user = new WorkspaceUserContext(7L, 1024L, 1024L);
        ContentBlobReference blob = new ContentBlobReference(13L, "blobs/pending-rollback", "text/plain", 5L);
        when(storedFileRepository.findActiveNodesByUserIdAndPathInAndFilenameIn(eq(7L), any(), any()))
                .thenReturn(java.util.List.of());
        when(contentBlobRegistrationApi.registerPendingBlob(anyString(), eq("text/plain"), eq(5L), anyString()))
                .thenReturn(blob);
        when(contentRegistrationApi.registerBlob(any()))
                .thenReturn(new RegisteredContentFile(33L, "a.txt", "/Docs", 5L, "text/plain", false, LocalDateTime.now()));

        TransactionSynchronizationManager.initSynchronization();
        try {
            WorkspaceFileIngressService.DeferredCreateStage stage = service.prepareDeferredCreate(
                    user,
                    "/Docs",
                    "a.txt",
                    "text/plain",
                    5L,
                    new ByteArrayInputStream("hello".getBytes(UTF_8))
            );

            Path tempFile = Path.of(stage.localTempPath());
            assertThat(Files.exists(tempFile)).isTrue();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            assertThat(Files.exists(tempFile)).isFalse();
            verify(contentBlobRegistrationApi, never()).markBlobFailed(anyLong());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldCleanupStoredWebDavBlobWhenMetadataRegistrationFails() {
        WorkspaceFileIngressService service = createService();
        WorkspaceUserContext user = new WorkspaceUserContext(7L, 1024L, 1024L);
        when(storedFileRepository.findActiveNodesByUserIdAndPathInAndFilenameIn(eq(7L), any(), any()))
                .thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            invocation.<java.util.function.Supplier<?>>getArgument(1).get();
            return null;
        }).when(contentBlobLifecycleApi).executeAfterBlobStored(anyString(), any());
        when(contentBlobRegistrationApi.registerStoredBlob(anyString(), eq("text/plain"), eq(5L)))
                .thenThrow(new IllegalStateException("register failed"));

        assertThatThrownBy(() -> service.storeWebDavFile(
                user,
                "/Docs",
                "a.txt",
                "text/plain",
                5L,
                new ByteArrayInputStream("hello".getBytes(UTF_8))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("register failed");

        verify(fileContentStorage).storeBlob(anyString(), eq("text/plain"), any(java.io.InputStream.class), eq(5L));
        verify(contentBlobLifecycleApi).executeAfterBlobStored(anyString(), any());
    }

    @Test
    void shouldDeleteStoredWebDavReplacementBlobWhenFileMetadataSaveFails() {
        ContentBlobLifecycleApi realLifecycle = new ContentBlobLifecycleService(null, null, fileContentStorage);
        WorkspaceFileIngressService service = createService(realLifecycle);
        WorkspaceUserContext user = new WorkspaceUserContext(7L, 1024L, 1024L);
        StoredFile existing = storedFile(22L, 7L, "/Docs", "a.txt", 90L, 91L);
        when(contentBlobRegistrationApi.registerStoredBlob(anyString(), eq("text/plain"), eq(5L)))
                .thenReturn(new ContentBlobReference(120L, "blobs/webdav-replace", "text/plain", 5L));
        when(contentAssetApi.createOrReferencePrimaryEntity(eq(7L), any()))
                .thenReturn(new ContentPrimaryEntity(121L, "blobs/webdav-replace", "text/plain", 5L, 1, null));
        when(storedFileRepository.save(existing)).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.replaceWebDavFileContent(
                user,
                existing,
                "text/plain",
                5L,
                3L,
                new ByteArrayInputStream("hello".getBytes(UTF_8))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        verify(fileContentStorage).storeBlob(anyString(), eq("text/plain"), any(java.io.InputStream.class), eq(5L));
        verify(fileContentStorage).deleteBlob(anyString());
    }

    private WorkspaceFileIngressService createService() {
        return createService(contentBlobLifecycleApi);
    }

    private WorkspaceFileIngressService createService(ContentBlobLifecycleApi lifecycleApi) {
        when(storageRuntimeProperties.getPendingBlobTempDir()).thenReturn(tempDir.toString());
        when(storedFileRepository.sumFileSizeByUserId(7L)).thenReturn(0L);
        WorkspaceNodeRulesService workspaceNodeRulesService = new WorkspaceNodeRulesService(
                new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage),
                (userId, recycleGroupItems, recycleOriginalPathResolver) -> {
                }
        );
        FileUploadRulesService fileUploadRulesService = new FileUploadRulesService(
                storedFileRepository,
                null,
                null,
                workspaceNodeRulesService,
                1024L * 1024L
        );
        return new WorkspaceFileIngressService(
                fileContentStorage,
                contentAssetApi,
                contentBlobQueryApi,
                contentRegistrationApi,
                contentBlobRegistrationApi,
                uploadCompletionApi,
                lifecycleApi,
                storedFileRepository,
                fileUploadRulesService,
                workspaceNodeRulesService,
                WorkspaceRequestProbe.disabled(),
                null,
                storageRuntimeProperties
        );
    }

    private StoredFile storedFile(Long id,
                                  Long userId,
                                  String path,
                                  String filename,
                                  Long blobId,
                                  Long primaryEntityId) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setUserId(userId);
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setDirectory(false);
        storedFile.setBlobId(blobId);
        storedFile.setPrimaryEntityId(primaryEntityId);
        storedFile.setContentType("text/plain");
        storedFile.setSize(5L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }
}
