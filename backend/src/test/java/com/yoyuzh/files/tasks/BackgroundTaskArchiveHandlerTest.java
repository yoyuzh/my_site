package com.yoyuzh.files.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskArchiveHandlerTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileService fileService;

    private ArchiveBackgroundTaskHandler handler;
    private ArgumentCaptor<byte[]> archiveBytesCaptor;

    @BeforeEach
    void setUp() {
        handler = new ArchiveBackgroundTaskHandler(
                storedFileRepository,
                userRepository,
                fileService,
                new ObjectMapper()
        );
        archiveBytesCaptor = ArgumentCaptor.forClass(byte[].class);
    }

    @Test
    void shouldArchiveDirectoryAndImportZipIntoSameParentPath() throws Exception {
        User user = createUser(7L);
        StoredFile directory = createDirectory(11L, user, "/docs", "archive");
        StoredFile nestedDirectory = createDirectory(12L, user, "/docs/archive", "nested");
        StoredFile childFile = createFile(13L, user, "/docs/archive", "notes.txt", "text/plain", "blobs/blob-13");
        StoredFile nestedFile = createFile(14L, user, "/docs/archive/nested", "todo.txt", "text/plain", "blobs/blob-14");
        FileMetadataResponse importedArchive = new FileMetadataResponse(
                99L,
                "archive.zip",
                "/docs",
                123L,
                "application/zip",
                false,
                null
        );

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(11L, 7L)).thenReturn(Optional.of(directory));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileService.summarizeArchiveSource(directory)).thenReturn(new FileService.ArchiveSourceSummary(2, 2));
        when(fileService.buildArchiveBytes(eq(directory), any())).thenReturn(buildArchiveBytes(Map.of(
                "archive/", "",
                "archive/nested/", "",
                "archive/notes.txt", "hello",
                "archive/nested/todo.txt", "world"
        )));
        when(fileService.importExternalFile(eq(user), eq("/docs"), eq("archive.zip"), eq("application/zip"), anyLong(), any(byte[].class)))
                .thenReturn(importedArchive);

        BackgroundTaskHandlerResult result = handler.handle(createArchiveTask(11L, 7L));

        verify(fileService).importExternalFile(
                eq(user),
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
        verify(fileService).buildArchiveBytes(eq(directory), any());
    }

    @Test
    void shouldArchiveSingleFileIntoZipWithoutLoadingDescendants() throws Exception {
        User user = createUser(7L);
        StoredFile file = createFile(21L, user, "/docs", "notes.txt", "text/plain", "blobs/blob-21");
        FileMetadataResponse importedArchive = new FileMetadataResponse(
                100L,
                "notes.txt.zip",
                "/docs",
                12L,
                "application/zip",
                false,
                null
        );

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(21L, 7L)).thenReturn(Optional.of(file));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileService.summarizeArchiveSource(file)).thenReturn(new FileService.ArchiveSourceSummary(1, 0));
        when(fileService.buildArchiveBytes(eq(file), any())).thenReturn(buildArchiveBytes(Map.of(
                "notes.txt", "hello"
        )));
        when(fileService.importExternalFile(eq(user), eq("/docs"), eq("notes.txt.zip"), eq("application/zip"), anyLong(), any(byte[].class)))
                .thenReturn(importedArchive);

        handler.handle(createArchiveTask(21L, 7L));

        verify(storedFileRepository, never()).findByUserIdAndPathEqualsOrDescendant(anyLong(), any());
        verify(fileService).importExternalFile(
                eq(user),
                eq("/docs"),
                eq("notes.txt.zip"),
                eq("application/zip"),
                anyLong(),
                archiveBytesCaptor.capture()
        );

        Map<String, String> entries = readZipEntries(archiveBytesCaptor.getValue());
        assertThat(entries).containsEntry("notes.txt", "hello");
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

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        return user;
    }

    private StoredFile createDirectory(Long id, User user, String path, String filename) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setPath(path);
        file.setFilename(filename);
        file.setDirectory(true);
        file.setSize(0L);
        return file;
    }

    private StoredFile createFile(Long id,
                                  User user,
                                  String path,
                                  String filename,
                                  String contentType,
                                  String objectKey) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
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
        file.setBlob(blob);
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
