package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WebDavWorkspacePutCommand;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMoveItemResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationResult;
import com.yoyuzh.files.workspace.api.WorkspaceQuotaGuard;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspacePathWriteApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private WorkspaceDirectoryApi workspaceDirectoryApi;

    @Mock
    private WorkspaceFileIngressService workspaceFileIngressService;

    @Mock
    private WorkspaceMutationApi workspaceMutationApi;

    @Mock
    private WorkspaceLifecycleApi workspaceLifecycleApi;

    @Mock
    private ContentBlobLifecycleApi contentBlobLifecycleApi;

    @Test
    void shouldCreateDirectoryByPath() {
        RuntimeWorkspacePathWriteApi api = createApi();
        FileMetadataResponse created = directoryResponse(10L, "/", "Docs");
        when(workspaceDirectoryApi.createDirectory(7L, "/Docs")).thenReturn(created);

        FileMetadataResponse response = api.createDirectoryByPath(7L, "/Docs");

        assertThat(response.directory()).isTrue();
        assertThat(response.filename()).isEqualTo("Docs");
    }

    @Test
    void shouldPutNewFileByPath() throws Exception {
        RuntimeWorkspacePathWriteApi api = createApi();
        WebDavWorkspacePutCommand command = command("/Docs/a.txt", false, "hello");
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.empty());
        when(workspaceFileIngressService.storeWebDavFile(
                any(),
                any(),
                any(),
                any(),
                any(Long.class),
                any(java.io.InputStream.class)
        )).thenReturn(new WorkspaceFileIngressService.CreatedFile(
                "/Docs",
                new com.yoyuzh.files.content.api.RegisteredContentFile(
                        20L,
                        "a.txt",
                        "/Docs",
                        5L,
                        "text/plain",
                        false,
                        LocalDateTime.now()
                )
        ));

        FileMetadataResponse response = api.putFileByPath(command);

        assertThat(response.filename()).isEqualTo("a.txt");
        assertThat(response.size()).isEqualTo(5L);
        verify(workspaceFileIngressService).storeWebDavFile(any(), eq("/Docs"), eq("a.txt"), eq("text/plain"), eq(5L), any());
    }

    @Test
    void shouldOverwriteExistingFileWhenRequested() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile existing = file(20L, 7L, "/Docs", "a.txt", false);
        existing.setBlobId(99L);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(existing));
        when(contentBlobLifecycleApi.collectBlobReferencesToDelete(List.of(99L)))
                .thenReturn(List.of(new ContentBlobReference(99L, "old-object", "text/plain", 3L)));
        when(workspaceFileIngressService.replaceWebDavFileContent(any(), any(StoredFile.class), any(), any(Long.class), any(Long.class), any()))
                .thenAnswer(invocation -> {
                    StoredFile file = invocation.getArgument(1);
                    file.setBlobId(100L);
                    file.setPrimaryEntityId(101L);
                    file.setLegacyStorageName("new-object");
                    file.setContentType(invocation.getArgument(2));
                    file.setSize(invocation.getArgument(3));
                    return new WorkspaceFileIngressService.ReplacementContent(100L, "new-object", 101L);
                });

        FileMetadataResponse response = api.putFileByPath(command("/Docs/a.txt", true, "new"));

        assertThat(response.filename()).isEqualTo("a.txt");
        assertThat(response.size()).isEqualTo(3L);
        verify(workspaceFileIngressService).replaceWebDavFileContent(any(), eq(existing), eq("text/plain"), eq(3L), eq(5L), any());
        verify(contentBlobLifecycleApi).deleteBlobReferences(any());
    }

    @Test
    void shouldRejectOverwriteWhenTargetIsDirectory() {
        RuntimeWorkspacePathWriteApi api = createApi();
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "Docs"))
                .thenReturn(Optional.of(file(21L, 7L, "/", "Docs", true)));

        assertThatThrownBy(() -> api.putFileByPath(command("/Docs", true, "x")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldMoveByPath() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile existing = file(20L, 7L, "/Docs", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(existing));
        WorkspaceMutationResult moved = new WorkspaceMutationResult(
                fileResponse(20L, "/Docs", "b.txt", 5L),
                "/Docs/a.txt",
                "/Docs/b.txt",
                List.of("/Docs"),
                true
        );
        when(workspaceMutationApi.rename(7L, 20L, "b.txt")).thenReturn(moved);

        WorkspaceMoveResult result = api.moveByPath(7L, "/Docs/a.txt", "/Docs/b.txt", false);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).filename()).isEqualTo("b.txt");
    }

    @Test
    void shouldOverwriteExistingTargetBeforeMoveByPath() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile source = file(20L, 7L, "/Docs", "a.txt", false);
        StoredFile target = file(30L, 7L, "/Archive", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(source));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Archive", "a.txt"))
                .thenReturn(Optional.of(target));
        WorkspaceLifecycleResult recycled = new WorkspaceLifecycleResult(
                fileResponse(30L, "/Archive", "a.txt", 5L),
                "/Archive/a.txt",
                "/.recycle/a.txt",
                List.of("/Archive")
        );
        when(workspaceLifecycleApi.recycle(7L, 30L)).thenReturn(recycled);
        WorkspaceMoveResult moved = WorkspaceMoveResult.success(List.of(new WorkspaceMoveItemResult(
                20L,
                "a.txt",
                "/Docs/a.txt",
                "/Archive/a.txt",
                false,
                false,
                null,
                null
        )));
        when(workspaceMutationApi.move(7L, 20L, "/Archive", null)).thenReturn(moved);

        WorkspaceMoveResult result = api.moveByPath(7L, "/Docs/a.txt", "/Archive/a.txt", true);

        assertThat(result.items()).hasSize(1);
        var ordered = inOrder(workspaceLifecycleApi, workspaceMutationApi);
        ordered.verify(workspaceLifecycleApi).recycle(7L, 30L);
        ordered.verify(workspaceMutationApi).move(7L, 20L, "/Archive", null);
    }

    @Test
    void shouldCopyByPathThroughWorkspaceLifecycleApi() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile existing = file(20L, 7L, "/Docs", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(existing));
        WorkspaceLifecycleResult copied = new WorkspaceLifecycleResult(
                fileResponse(30L, "/Archive", "a.txt", 5L),
                "/Docs/a.txt",
                "/Archive/a.txt",
                List.of("/Archive")
        );
        when(workspaceLifecycleApi.copy(eq(7L), eq(20L), eq("/Archive"), any(WorkspaceQuotaGuard.class)))
                .thenReturn(copied);

        WorkspaceLifecycleResult result = api.copyByPath(7L, "/Docs/a.txt", "/Archive/a.txt", false, bytes -> { });

        assertThat(result.toPath()).isEqualTo("/Archive/a.txt");
        verify(workspaceLifecycleApi).copy(eq(7L), eq(20L), eq("/Archive"), any(WorkspaceQuotaGuard.class));
    }

    @Test
    void shouldOverwriteExistingTargetBeforeCopyByPath() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile source = file(20L, 7L, "/Docs", "a.txt", false);
        StoredFile target = file(30L, 7L, "/Archive", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(source));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Archive", "a.txt"))
                .thenReturn(Optional.of(target));
        WorkspaceLifecycleResult recycled = new WorkspaceLifecycleResult(
                fileResponse(30L, "/Archive", "a.txt", 5L),
                "/Archive/a.txt",
                "/.recycle/a.txt",
                List.of("/Archive")
        );
        when(workspaceLifecycleApi.recycle(7L, 30L)).thenReturn(recycled);
        WorkspaceLifecycleResult copied = new WorkspaceLifecycleResult(
                fileResponse(31L, "/Archive", "a.txt", 5L),
                "/Docs/a.txt",
                "/Archive/a.txt",
                List.of("/Archive")
        );
        when(workspaceLifecycleApi.copy(eq(7L), eq(20L), eq("/Archive"), any(WorkspaceQuotaGuard.class)))
                .thenReturn(copied);

        WorkspaceLifecycleResult result = api.copyByPath(7L, "/Docs/a.txt", "/Archive/a.txt", true, bytes -> { });

        assertThat(result.toPath()).isEqualTo("/Archive/a.txt");
        var ordered = inOrder(workspaceLifecycleApi);
        ordered.verify(workspaceLifecycleApi).recycle(7L, 30L);
        ordered.verify(workspaceLifecycleApi).copy(eq(7L), eq(20L), eq("/Archive"), any(WorkspaceQuotaGuard.class));
    }

    @Test
    void shouldNotRecycleSourceWhenCopyTargetIsSameNode() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile source = file(20L, 7L, "/Docs", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(source));
        WorkspaceLifecycleResult copied = new WorkspaceLifecycleResult(
                fileResponse(31L, "/Docs", "a.txt", 5L),
                "/Docs/a.txt",
                "/Docs/a.txt",
                List.of("/Docs")
        );
        when(workspaceLifecycleApi.copy(eq(7L), eq(20L), eq("/Docs"), any(WorkspaceQuotaGuard.class)))
                .thenReturn(copied);

        WorkspaceLifecycleResult result = api.copyByPath(7L, "/Docs/a.txt", "/Docs/a.txt", true, bytes -> { });

        assertThat(result.toPath()).isEqualTo("/Docs/a.txt");
        verify(workspaceLifecycleApi, never()).recycle(7L, 20L);
        verify(workspaceLifecycleApi).copy(eq(7L), eq(20L), eq("/Docs"), any(WorkspaceQuotaGuard.class));
    }

    @Test
    void shouldRecycleByPath() {
        RuntimeWorkspacePathWriteApi api = createApi();
        StoredFile existing = file(20L, 7L, "/Docs", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(existing));
        WorkspaceLifecycleResult recycled = new WorkspaceLifecycleResult(
                fileResponse(20L, "/Docs", "a.txt", 5L),
                "/Docs/a.txt",
                "/.recycle/a.txt",
                List.of("/Docs")
        );
        when(workspaceLifecycleApi.recycle(7L, 20L)).thenReturn(recycled);

        WorkspaceLifecycleResult result = api.recycleByPath(7L, "/Docs/a.txt");

        assertThat(result.file().filename()).isEqualTo("a.txt");
    }

    private RuntimeWorkspacePathWriteApi createApi() {
        return new RuntimeWorkspacePathWriteApi(
                storedFileRepository,
                workspaceDirectoryApi,
                workspaceFileIngressService,
                workspaceMutationApi,
                workspaceLifecycleApi,
                contentBlobLifecycleApi,
                new RuntimeWorkspacePathPolicy(storedFileRepository, null),
                WorkspaceRequestProbe.disabled()
        );
    }

    private WebDavWorkspacePutCommand command(String logicalPath, boolean overwrite, String body) {
        byte[] bytes = body.getBytes(UTF_8);
        return new WebDavWorkspacePutCommand(
                new WorkspaceUserContext(7L, 1024L, 512L),
                logicalPath,
                "text/plain",
                bytes.length,
                new ByteArrayInputStream(bytes),
                overwrite
        );
    }

    private StoredFile file(Long id, Long userId, String path, String filename, boolean directory) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setUserId(userId);
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setDirectory(directory);
        storedFile.setContentType(directory ? "directory" : "text/plain");
        storedFile.setSize(directory ? 0L : 5L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }

    private FileMetadataResponse directoryResponse(Long id, String path, String filename) {
        return new FileMetadataResponse(id, filename, path, 0L, "directory", true, LocalDateTime.now(), LocalDateTime.now(), null, null, false);
    }

    private FileMetadataResponse fileResponse(Long id, String path, String filename, long size) {
        return new FileMetadataResponse(id, filename, path, size, "text/plain", false, LocalDateTime.now(), LocalDateTime.now(), null, null, false);
    }
}
