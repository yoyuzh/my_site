package com.yoyuzh.files.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
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
    private StoredFileRepository storedFileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileService fileService;

    private ExtractBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExtractBackgroundTaskHandler(
                storedFileRepository,
                userRepository,
                fileService,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldExtractArchivedDirectoryIntoSiblingFolder() throws Exception {
        User user = createUser(7L);
        StoredFile archive = createArchiveFile(11L, user, "/docs", "archive.zip", "application/zip", "blobs/archive.zip");

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(11L, 7L)).thenReturn(Optional.of(archive));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileService.readZipCompatibleArchive(archive)).thenReturn(new FileService.ZipCompatibleArchive(
                java.util.List.of(
                        new FileService.ZipCompatibleArchiveEntry("archive", true, new byte[0]),
                        new FileService.ZipCompatibleArchiveEntry("archive/nested", true, new byte[0]),
                        new FileService.ZipCompatibleArchiveEntry("archive/notes.txt", false, "hello".getBytes(StandardCharsets.UTF_8)),
                        new FileService.ZipCompatibleArchiveEntry("archive/nested/todo.txt", false, "world".getBytes(StandardCharsets.UTF_8))
                ),
                "archive"
        ));

        BackgroundTaskHandlerResult result = handler.handle(createExtractTask(11L, 7L, "archive"));

        verify(fileService).importExternalFilesAtomically(
                eq(user),
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
        User user = createUser(7L);
        StoredFile archive = createArchiveFile(21L, user, "/docs", "notes.txt.zip", "application/zip", "blobs/notes.txt.zip");

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(21L, 7L)).thenReturn(Optional.of(archive));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileService.readZipCompatibleArchive(archive)).thenReturn(new FileService.ZipCompatibleArchive(
                java.util.List.of(
                        new FileService.ZipCompatibleArchiveEntry("notes.txt", false, "hello".getBytes(StandardCharsets.UTF_8))
                ),
                null
        ));

        BackgroundTaskHandlerResult result = handler.handle(createExtractTask(21L, 7L, "notes.txt"));

        verify(fileService).importExternalFilesAtomically(
                eq(user),
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
        User user = createUser(7L);
        StoredFile archive = createArchiveFile(31L, user, "/docs", "backup.7z", "application/x-7z-compressed", "blobs/backup.7z");

        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(31L, 7L)).thenReturn(Optional.of(archive));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileService.readZipCompatibleArchive(archive))
                .thenThrow(new BusinessException(ErrorCode.UNKNOWN, "压缩包读取失败"));

        assertThatThrownBy(() -> handler.handle(createExtractTask(31L, 7L, "backup")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("extract task only supports zip-compatible archives");

        verify(fileService, never()).importExternalFilesAtomically(any(), any(), any(), any());
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

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        return user;
    }

    private StoredFile createArchiveFile(Long id,
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
        file.setSize(12L);
        FileBlob blob = new FileBlob();
        blob.setId(id + 1000);
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(12L);
        file.setBlob(blob);
        return file;
    }
}
