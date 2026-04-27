package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveApi;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveSummary;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskArchiveHandlerTest {

    @Mock
    private IdentityUserDirectoryApi identityUserDirectoryApi;
    @Mock
    private WorkspaceArchiveApi workspaceArchiveApi;
    @Mock
    private WorkspaceBootstrapApi workspaceBootstrapApi;

    private ArchiveBackgroundTaskHandler handler;
    private ArgumentCaptor<byte[]> archiveBytesCaptor;

    @BeforeEach
    void setUp() {
        handler = new ArchiveBackgroundTaskHandler(
                identityUserDirectoryApi,
                workspaceArchiveApi,
                workspaceBootstrapApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
        archiveBytesCaptor = ArgumentCaptor.forClass(byte[].class);
    }

    @Test
    void shouldArchiveDirectoryAndImportZipIntoSameParentPath() throws Exception {
        FileMetadataResponse importedArchive = new FileMetadataResponse(
                99L,
                "archive.zip",
                "/docs",
                123L,
                "application/zip",
                false,
                null,
                null,
                false
        );

        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.summarizeArchiveSource(7L, 11L)).thenReturn(new WorkspaceArchiveSummary(2, 2));
        when(workspaceArchiveApi.buildArchiveBytes(eq(7L), eq(11L), any())).thenReturn(buildArchiveBytes(Map.of(
                "archive/", "",
                "archive/nested/", "",
                "archive/notes.txt", "hello",
                "archive/nested/todo.txt", "world"
        )));
        when(workspaceBootstrapApi.importExternalFile(any(WorkspaceUserContext.class), eq("/docs"), eq("archive.zip"), eq("application/zip"), anyLong(), any(byte[].class)))
                .thenReturn(importedArchive);

        BackgroundTaskHandlerResult result = handler.handle(createArchiveTask(11L, 7L));

        verify(workspaceBootstrapApi).importExternalFile(
                argThat(context -> context.userId().equals(7L)),
                eq("/docs"),
                eq("archive.zip"),
                eq("application/zip"),
                anyLong(),
                archiveBytesCaptor.capture()
        );

        Map<String, String> entries = readZipEntries(archiveBytesCaptor.getValue());

        assertThat(entries).containsEntry("archive/", "");
        assertThat(entries).containsEntry("archive/nested/", "");
        assertThat(entries).containsEntry("archive/notes.txt", "hello");
        assertThat(entries).containsEntry("archive/nested/todo.txt", "world");
        assertThat(result.publicStatePatch()).containsEntry("worker", "archive");
        assertThat(result.publicStatePatch()).containsEntry("archivedFileId", 99L);
        assertThat(result.publicStatePatch()).containsEntry("archivedFilename", "archive.zip");
        assertThat(result.publicStatePatch()).containsEntry("archivedPath", "/docs");
        assertThat(result.publicStatePatch()).containsEntry("processedFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalFileCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("processedDirectoryCount", 2);
        assertThat(result.publicStatePatch()).containsEntry("totalDirectoryCount", 2);
        verify(workspaceArchiveApi).buildArchiveBytes(eq(7L), eq(11L), any());
    }

    @Test
    void shouldArchiveSingleFileIntoZipWithoutLoadingDescendants() throws Exception {
        FileMetadataResponse importedArchive = new FileMetadataResponse(
                100L,
                "notes.txt.zip",
                "/docs",
                12L,
                "application/zip",
                false,
                null,
                null,
                false
        );

        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(Optional.of(createUser(7L)));
        when(workspaceArchiveApi.summarizeArchiveSource(7L, 21L)).thenReturn(new WorkspaceArchiveSummary(1, 0));
        when(workspaceArchiveApi.buildArchiveBytes(eq(7L), eq(21L), any())).thenReturn(buildArchiveBytes(Map.of(
                "notes.txt", "hello"
        )));
        when(workspaceBootstrapApi.importExternalFile(any(WorkspaceUserContext.class), eq("/docs"), eq("notes.txt.zip"), eq("application/zip"), anyLong(), any(byte[].class)))
                .thenReturn(importedArchive);

        handler.handle(createArchiveTask(21L, 7L));

        verify(workspaceBootstrapApi).importExternalFile(
                argThat(context -> context.userId().equals(7L)),
                eq("/docs"),
                eq("notes.txt.zip"),
                eq("application/zip"),
                anyLong(),
                archiveBytesCaptor.capture()
        );

        Map<String, String> entries = readZipEntries(archiveBytesCaptor.getValue());
        assertThat(entries).containsEntry("notes.txt", "hello");
    }

    @Test
    void shouldNotHoldClassLevelTransactionBoundary() {
        assertThat(ArchiveBackgroundTaskHandler.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private BackgroundTask createArchiveTask(Long fileId, Long userId) {
        BackgroundTask task = new BackgroundTask();
        task.setId(301L);
        task.setType(BackgroundTaskType.ARCHIVE);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(userId);
        task.setPublicStateJson("{\"fileId\":" + fileId + "}");
        task.setPrivateStateJson("{\"fileId\":" + fileId + ",\"taskType\":\"ARCHIVE\",\"outputPath\":\"/docs\",\"outputFilename\":\""
                + (fileId.equals(21L) ? "notes.txt.zip" : "archive.zip") + "\"}");
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

    private StoredFile createDirectory(Long id, String path, String filename) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setPath(path);
        file.setFilename(filename);
        file.setDirectory(true);
        file.setSize(0L);
        return file;
    }

    private StoredFile createFile(Long id,
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
        file.setSize(5L);
        FileBlob blob = new FileBlob();
        blob.setId(id + 1000);
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(5L);
        file.setBlobId(blob == null ? null : blob.getId());
        return file;
    }

    private Map<String, String> readZipEntries(byte[] archiveBytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            var entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), entry.isDirectory() ? "" : new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                entry = zipInputStream.getNextEntry();
            }
        }
        return entries;
    }

    private byte[] buildArchiveBytes(Map<String, String> entries) throws Exception {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
