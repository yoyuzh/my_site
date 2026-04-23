package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceExternalFileImport;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.api.WorkspaceZipArchive;
import com.yoyuzh.files.workspace.api.WorkspaceZipArchiveEntry;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractBackgroundTaskHandlerTest {

    @Mock
    private IdentityUserDirectoryApi identityUserDirectoryApi;
    @Mock
    private WorkspaceArchiveApi workspaceArchiveApi;
    @Mock
    private WorkspaceBootstrapApi workspaceBootstrapApi;

    private ExtractBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExtractBackgroundTaskHandler(
                identityUserDirectoryApi,
                workspaceArchiveApi,
                workspaceBootstrapApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldExtractArchivedDirectoryIntoSiblingFolder() throws Exception {
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.readZipCompatibleArchive(7L, 11L)).thenReturn(new WorkspaceZipArchive(
                java.util.List.of(
                        new WorkspaceZipArchiveEntry("archive", true, new byte[0]),
                        new WorkspaceZipArchiveEntry("archive/nested", true, new byte[0]),
                        new WorkspaceZipArchiveEntry("archive/notes.txt", false, "hello".getBytes(StandardCharsets.UTF_8)),
                        new WorkspaceZipArchiveEntry("archive/nested/todo.txt", false, "world".getBytes(StandardCharsets.UTF_8))
                ),
                "archive"
        ));

        BackgroundTaskHandlerResult result = handler.handle(createExtractTask(11L, 7L, "archive"));

        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                argThat(context -> context.userId().equals(7L)),
                eq(java.util.List.of("/docs/archive", "/docs/archive/nested")),
                argThat(files -> files.size() == 2
                        && files.stream().anyMatch(file -> "/docs/archive".equals(file.path())
                        && "notes.txt".equals(file.filename())
                        && "text/plain".equals(file.contentType())
                        && java.util.Arrays.equals("hello".getBytes(StandardCharsets.UTF_8), file.content()))
                        && files.stream().anyMatch(file -> "/docs/archive/nested".equals(file.path())
                        && "todo.txt".equals(file.filename())
                        && "text/plain".equals(file.contentType())
                        && java.util.Arrays.equals("world".getBytes(StandardCharsets.UTF_8), file.content()))),
                any()
        );
        assertThat(result.publicStatePatch()).containsEntry("worker", "extract");
        assertThat(result.publicStatePatch()).containsEntry("extractedPath", "/docs/archive");
        assertThat(result.publicStatePatch()).containsEntry("extractedFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("extractedDirectoryCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("processedFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("processedDirectoryCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalDirectoryCount", 2);
    }

    @Test
    void shouldExtractSingleArchivedFileBackIntoParentPath() throws Exception {
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.readZipCompatibleArchive(7L, 21L)).thenReturn(new WorkspaceZipArchive(
                java.util.List.of(
                        new WorkspaceZipArchiveEntry("notes.txt", false, "hello".getBytes(StandardCharsets.UTF_8))
                ),
                null
        ));

        BackgroundTaskHandlerResult result = handler.handle(createExtractTask(21L, 7L, "notes.txt"));

        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                argThat(context -> context.userId().equals(7L)),
                eq(java.util.List.of()),
                argThat(files -> files.size() == 1
                        && "/docs".equals(files.get(0).path())
                        && "notes.txt".equals(files.get(0).filename())
                        && "text/plain".equals(files.get(0).contentType())
                        && java.util.Arrays.equals("hello".getBytes(StandardCharsets.UTF_8), files.get(0).content())),
                any()
        );
        assertThat(result.publicStatePatch()).containsEntry("worker", "extract");
        assertThat(result.publicStatePatch()).containsEntry("extractedPath", "/docs");
        assertThat(result.publicStatePatch()).containsEntry("extractedFileCount", 1);
        assertThat(result.publicStatePatch()).containsEntry("extractedDirectoryCount", 0);
        assertThat(result.publicStatePatch()).containsEntry("processedFileCount", 1);
        assertThat(result.publicStatePatch()).containsEntry("totalFileCount", 1);
        assertThat(result.publicStatePatch()).containsEntry("processedDirectoryCount", 0);
        assertThat(result.publicStatePatch()).containsEntry("totalDirectoryCount", 0);
    }

    @Test
    void shouldRejectNonZipCompatibleArchiveContent() {
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.readZipCompatibleArchive(7L, 31L))
                .thenThrow(new BusinessException(ErrorCode.UNKNOWN, "压缩包读取失败"));

        assertThatThrownBy(() -> handler.handle(createExtractTask(31L, 7L, "backup")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("extract task only supports zip-compatible archives");

        verify(workspaceBootstrapApi, never()).importExternalFilesAtomically(any(), any(), any(), any());
    }

    private BackgroundTask createExtractTask(Long fileId, Long userId, String outputDirectoryName) {
        BackgroundTask task = new BackgroundTask();
        task.setId(401L);
        task.setType(BackgroundTaskType.EXTRACT);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(userId);
        task.setPublicStateJson("""
                {"fileId":%d,"outputPath":"/docs","outputDirectoryName":"%s"}
                """.formatted(fileId, outputDirectoryName));
        task.setPrivateStateJson("""
                {"fileId":%d,"taskType":"EXTRACT","outputPath":"/docs","outputDirectoryName":"%s"}
                """.formatted(fileId, outputDirectoryName));
        return task;
    }

    private IdentityUserSnapshot createUser(Long id) {
        return new IdentityUserSnapshot(
                id,
                "alice",
                "Alice",
                "alice@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                IdentityRoleName.USER,
                null,
                1024L,
                1024L
        );
    }

    private StoredFile createArchiveFile(Long id,
                                         String path,
                                         String filename,
                                         String contentType,
                                         String objectKey) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setPath(path);
        file.setFilename(filename);
        file.setDirectory(false);
        file.setContentType(contentType);
        file.setSize(12L);
        FileBlob blob = new FileBlob();
        blob.setId(id + 1000);
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(12L);
        file.setBlobId(blob == null ? null : blob.getId());
        return file;
    }
}
