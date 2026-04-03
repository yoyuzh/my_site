package com.yoyuzh.files;

import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileBlobRepository fileBlobRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private FileShareLinkRepository fileShareLinkRepository;
    @Mock
    private AdminMetricsService adminMetricsService;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        fileService = new FileService(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                fileShareLinkRepository,
                adminMetricsService,
                properties
        );
    }

    @Test
    void shouldStoreUploadedFileViaConfiguredStorage() {
        User user = createUser(7L);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(100L);
            return blob;
        });
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(10L);
            return file;
        });

        FileMetadataResponse response = fileService.upload(user, "/docs", multipartFile);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.path()).isEqualTo("/docs");
        verify(fileContentStorage).uploadBlob(org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("blobs/")), eq(multipartFile));
        verify(fileBlobRepository).save(org.mockito.ArgumentMatchers.argThat(blob ->
                blob.getObjectKey() != null
                        && blob.getObjectKey().startsWith("blobs/")
                        && blob.getSize().equals(5L)
                        && "text/plain".equals(blob.getContentType())));
    }

    @Test
    void shouldInitiateDirectUploadThroughStorage() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(fileContentStorage.prepareBlobUpload(eq("/docs"), eq("notes.txt"), org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("blobs/")), eq("text/plain"), eq(12L)))
                .thenReturn(new PreparedUpload(true, "https://upload.example.com", "PUT", Map.of("Content-Type", "text/plain"), "blobs/upload-1"));

        InitiateUploadResponse response = fileService.initiateUpload(user,
                new InitiateUploadRequest("/docs", "notes.txt", "text/plain", 12L));

        assertThat(response.direct()).isTrue();
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com");
        assertThat(response.storageName()).startsWith("blobs/");
        verify(fileContentStorage).prepareBlobUpload(eq("/docs"), eq("notes.txt"), org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("blobs/")), eq("text/plain"), eq(12L));
    }

    @Test
    void shouldAllowInitiatingUploadAtFiveHundredMegabytes() {
        User user = createUser(7L);
        long uploadSize = 500L * 1024 * 1024;
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "movie.zip")).thenReturn(false);
        when(fileContentStorage.prepareBlobUpload(eq("/docs"), eq("movie.zip"), org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("blobs/")), eq("application/zip"), eq(uploadSize)))
                .thenReturn(new PreparedUpload(true, "https://upload.example.com", "PUT", Map.of(), "blobs/upload-2"));

        InitiateUploadResponse response = fileService.initiateUpload(user,
                new InitiateUploadRequest("/docs", "movie.zip", "application/zip", uploadSize));

        assertThat(response.direct()).isTrue();
        verify(fileContentStorage).prepareBlobUpload(eq("/docs"), eq("movie.zip"), org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("blobs/")), eq("application/zip"), eq(uploadSize));
    }

    @Test
    void shouldCompleteDirectUploadAndPersistMetadata() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(101L);
            return blob;
        });
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(11L);
            return file;
        });

        FileMetadataResponse response = fileService.completeUpload(user,
                new CompleteUploadRequest("/docs", "notes.txt", "blobs/upload-3", "text/plain", 12L));

        assertThat(response.id()).isEqualTo(11L);
        verify(fileContentStorage).completeBlobUpload("blobs/upload-3", "text/plain", 12L);
    }

    @Test
    void shouldDeleteUploadedBlobWhenMetadataSaveFails() {
        User user = createUser(7L);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(true);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("insert failed")).when(storedFileRepository).save(any(StoredFile.class));

        assertThatThrownBy(() -> fileService.upload(user, "/docs", multipartFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert failed");

        verify(fileContentStorage).deleteBlob(org.mockito.ArgumentMatchers.argThat(
                key -> key != null && key.startsWith("blobs/")));
    }

    @Test
    void shouldDeleteCompletedUploadBlobWhenMetadataSaveFails() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(true);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("insert failed")).when(storedFileRepository).save(any(StoredFile.class));

        assertThatThrownBy(() -> fileService.completeUpload(user,
                new CompleteUploadRequest("/docs", "notes.txt", "blobs/upload-fail", "text/plain", 12L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert failed");

        verify(fileContentStorage).deleteBlob("blobs/upload-fail");
    }

    @Test
    void shouldCreateMissingDirectoriesBeforeCompletingNestedUpload() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/projects/site", "logo.png")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "projects")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/projects", "site")).thenReturn(false);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fileService.completeUpload(user,
                new CompleteUploadRequest("/projects/site", "logo.png", "blobs/upload-4", "image/png", 12L));

        verify(fileContentStorage).ensureDirectory(7L, "/projects");
        verify(fileContentStorage).ensureDirectory(7L, "/projects/site");
        verify(fileContentStorage).completeBlobUpload("blobs/upload-4", "image/png", 12L);
        verify(storedFileRepository, times(3)).save(any(StoredFile.class));
    }

    @Test
    void shouldRenameFileThroughConfiguredStorage() {
        User user = createUser(7L);
        StoredFile storedFile = createFile(10L, user, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(storedFile));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "renamed.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.rename(user, 10L, "renamed.txt");

        assertThat(response.filename()).isEqualTo("renamed.txt");
        verify(fileContentStorage, never()).renameFile(any(), any(), any(), any());
    }

    @Test
    void shouldRenameDirectoryAndUpdateDescendantPaths() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt");

        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "renamed-archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.rename(user, 10L, "renamed-archive");

        assertThat(response.filename()).isEqualTo("renamed-archive");
        assertThat(childFile.getPath()).isEqualTo("/docs/renamed-archive");
        verify(fileContentStorage, never()).renameDirectory(any(), any(), any(), any());
    }

    @Test
    void shouldMoveFileToAnotherDirectory() {
        User user = createUser(7L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "下载");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "下载")).thenReturn(Optional.of(targetDirectory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/下载", "notes.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.move(user, 10L, "/下载");

        assertThat(response.path()).isEqualTo("/下载");
        assertThat(file.getPath()).isEqualTo("/下载");
        verify(fileContentStorage, never()).moveFile(any(), any(), any(), any());
    }

    @Test
    void shouldMoveDirectoryAndUpdateDescendantPaths() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "图片");
        StoredFile childFile = createFile(12L, user, "/docs/archive", "nested.txt");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "图片")).thenReturn(Optional.of(targetDirectory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片", "archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storedFileRepository.saveAll(List.of(childFile))).thenReturn(List.of(childFile));

        FileMetadataResponse response = fileService.move(user, 10L, "/图片");

        assertThat(response.path()).isEqualTo("/图片/archive");
        assertThat(directory.getPath()).isEqualTo("/图片");
        assertThat(childFile.getPath()).isEqualTo("/图片/archive");
        verify(fileContentStorage, never()).renameDirectory(any(), any(), any(), any());
    }

    @Test
    void shouldRejectMovingDirectoryIntoItsOwnDescendant() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile docsDirectory = createDirectory(11L, user, "/", "docs");
        StoredFile archiveDirectory = createDirectory(12L, user, "/docs", "archive");
        StoredFile descendantDirectory = createDirectory(13L, user, "/docs/archive", "nested");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs"))
                .thenReturn(Optional.of(docsDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs", "archive"))
                .thenReturn(Optional.of(archiveDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs/archive", "nested"))
                .thenReturn(Optional.of(descendantDirectory));

        assertThatThrownBy(() -> fileService.move(user, 10L, "/docs/archive/nested"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能移动到当前目录或其子目录");
    }

    @Test
    void shouldCopyFileToAnotherDirectory() {
        User user = createUser(7L);
        FileBlob blob = createBlob(50L, "blobs/blob-copy", 5L, "text/plain");
        StoredFile file = createFile(10L, user, "/docs", "notes.txt", blob);
        StoredFile targetDirectory = createDirectory(11L, user, "/", "下载");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "下载")).thenReturn(Optional.of(targetDirectory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/下载", "notes.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            if (storedFile.getId() == null) {
                storedFile.setId(20L);
            }
            return storedFile;
        });

        FileMetadataResponse response = fileService.copy(user, 10L, "/下载");

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.path()).isEqualTo("/下载");
        assertThat(file.getBlob()).isSameAs(blob);
        verify(fileContentStorage, never()).copyFile(any(), any(), any(), any());
    }

    @Test
    void shouldCopyDirectoryAndDescendants() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "图片");
        StoredFile childDirectory = createDirectory(12L, user, "/docs/archive", "nested");
        FileBlob childBlob = createBlob(51L, "blobs/blob-archive-1", 5L, "text/plain");
        FileBlob nestedBlob = createBlob(52L, "blobs/blob-archive-2", 5L, "text/plain");
        StoredFile childFile = createFile(13L, user, "/docs/archive", "notes.txt", childBlob);
        StoredFile nestedFile = createFile(14L, user, "/docs/archive/nested", "todo.txt", nestedBlob);
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "图片")).thenReturn(Optional.of(targetDirectory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片", "archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive"))
                .thenReturn(List.of(childDirectory, childFile, nestedFile));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片/archive", "nested")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片/archive", "notes.txt")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片/archive/nested", "todo.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            if (storedFile.getId() == null) {
                storedFile.setId(100L + storedFile.getFilename().length());
            }
            return storedFile;
        });

        FileMetadataResponse response = fileService.copy(user, 10L, "/图片");

        assertThat(response.path()).isEqualTo("/图片/archive");
        verify(fileContentStorage, never()).copyFile(any(), any(), any(), any());
    }

    @Test
    void shouldRejectCopyingDirectoryIntoItsOwnDescendant() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile docsDirectory = createDirectory(11L, user, "/", "docs");
        StoredFile archiveDirectory = createDirectory(12L, user, "/docs", "archive");
        StoredFile descendantDirectory = createDirectory(13L, user, "/docs/archive", "nested");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs"))
                .thenReturn(Optional.of(docsDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs", "archive"))
                .thenReturn(Optional.of(archiveDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs/archive", "nested"))
                .thenReturn(Optional.of(descendantDirectory));

        assertThatThrownBy(() -> fileService.copy(user, 10L, "/docs/archive/nested"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能复制到当前目录或其子目录");
    }

    @Test
    void shouldRejectDeletingOtherUsersFile() {
        User owner = createUser(1L);
        User requester = createUser(2L);
        StoredFile storedFile = createFile(100L, owner, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(100L)).thenReturn(Optional.of(storedFile));

        assertThatThrownBy(() -> fileService.delete(requester, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有权限");
    }

    @Test
    void shouldDeleteDirectoryWithNestedFilesViaStorage() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        FileBlob blob = createBlob(60L, "blobs/blob-delete", 5L, "text/plain");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt", blob);

        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.countByBlobId(60L)).thenReturn(1L);

        fileService.delete(user, 10L);

        verify(fileContentStorage).deleteBlob("blobs/blob-delete");
        verify(fileBlobRepository).delete(blob);
        verify(storedFileRepository).deleteAll(List.of(childFile));
        verify(storedFileRepository).delete(directory);
    }

    @Test
    void shouldDeleteSharedBlobOnlyWhenLastReferenceIsRemoved() {
        User user = createUser(7L);
        FileBlob blob = createBlob(70L, "blobs/blob-shared", 5L, "text/plain");
        StoredFile storedFile = createFile(15L, user, "/docs", "shared.txt", blob);
        when(storedFileRepository.findDetailedById(15L)).thenReturn(Optional.of(storedFile));
        when(storedFileRepository.countByBlobId(70L)).thenReturn(2L);

        fileService.delete(user, 15L);

        verify(fileContentStorage, never()).deleteBlob(any());
        verify(fileBlobRepository, never()).delete(any());
        verify(storedFileRepository).delete(storedFile);
    }

    @Test
    void shouldDeleteBlobObjectWhenLastReferenceIsRemoved() {
        User user = createUser(7L);
        FileBlob blob = createBlob(71L, "blobs/blob-last", 5L, "text/plain");
        StoredFile storedFile = createFile(16L, user, "/docs", "last.txt", blob);
        when(storedFileRepository.findDetailedById(16L)).thenReturn(Optional.of(storedFile));
        when(storedFileRepository.countByBlobId(71L)).thenReturn(1L);

        fileService.delete(user, 16L);

        verify(fileContentStorage).deleteBlob("blobs/blob-last");
        verify(fileBlobRepository).delete(blob);
        verify(storedFileRepository).delete(storedFile);
    }

    @Test
    void shouldListFilesByPathWithPagination() {
        User user = createUser(7L);
        StoredFile file = createFile(100L, user, "/docs", "notes.txt");
        when(storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                7L, "/docs", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(file)));

        var result = fileService.list(user, "/docs", 0, 10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).filename()).isEqualTo("notes.txt");
    }

    @Test
    void shouldCreateDefaultDirectoriesForUserWorkspace() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "下载")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "文档")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "图片")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fileService.ensureDefaultDirectories(user);

        verify(fileContentStorage).ensureDirectory(7L, "/下载");
        verify(fileContentStorage).ensureDirectory(7L, "/文档");
        verify(fileContentStorage).ensureDirectory(7L, "/图片");
        verify(storedFileRepository, times(3)).save(any(StoredFile.class));
    }

    @Test
    void shouldUseSignedDownloadUrlWhenStorageSupportsDirectDownload() {
        User user = createUser(7L);
        StoredFile file = createFile(22L, user, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(22L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createBlobDownloadUrl("blobs/blob-22", "notes.txt"))
                .thenReturn("https://download.example.com/file");

        DownloadUrlResponse response = fileService.getDownloadUrl(user, 22L);

        assertThat(response.url()).isEqualTo("https://download.example.com/file");
    }

    @Test
    void shouldFallbackToBackendDownloadUrlWhenStorageIsLocal() {
        User user = createUser(7L);
        StoredFile file = createFile(22L, user, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(22L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(false);

        DownloadUrlResponse response = fileService.getDownloadUrl(user, 22L);

        assertThat(response.url()).isEqualTo("/api/files/download/22");
        verify(fileContentStorage, never()).createDownloadUrl(any(), any(), any(), any());
    }

    @Test
    void shouldDownloadDirectoryAsZipArchive() throws Exception {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childDirectory = createDirectory(11L, user, "/docs/archive", "nested");
        StoredFile childFile = createFile(12L, user, "/docs/archive", "notes.txt");
        StoredFile nestedFile = createFile(13L, user, "/docs/archive/nested", "todo.txt");

        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive"))
                .thenReturn(List.of(childDirectory, childFile, nestedFile));
        when(fileContentStorage.readBlob("blobs/blob-12"))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));
        when(fileContentStorage.readBlob("blobs/blob-13"))
                .thenReturn("world".getBytes(StandardCharsets.UTF_8));

        var response = fileService.download(user, 10L);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("archive.zip");
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("application/zip"));

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream((byte[]) response.getBody()), StandardCharsets.UTF_8)) {
            var entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), entry.isDirectory() ? "" : new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                entry = zipInputStream.getNextEntry();
            }
        }

        assertThat(entries).containsEntry("archive/", "");
        assertThat(entries).containsEntry("archive/nested/", "");
        assertThat(entries).containsEntry("archive/notes.txt", "hello");
        assertThat(entries).containsEntry("archive/nested/todo.txt", "world");
        verify(fileContentStorage).readBlob("blobs/blob-12");
        verify(fileContentStorage).readBlob("blobs/blob-13");
    }

    @Test
    void shouldCreateShareLinkForOwnedFile() {
        User user = createUser(7L);
        StoredFile file = createFile(22L, user, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(22L)).thenReturn(Optional.of(file));
        when(fileShareLinkRepository.save(any(FileShareLink.class))).thenAnswer(invocation -> {
            FileShareLink shareLink = invocation.getArgument(0);
            shareLink.setId(100L);
            shareLink.setToken("share-token-1");
            return shareLink;
        });

        CreateFileShareLinkResponse response = fileService.createShareLink(user, 22L);

        assertThat(response.token()).isEqualTo("share-token-1");
        assertThat(response.filename()).isEqualTo("notes.txt");
        verify(fileShareLinkRepository).save(any(FileShareLink.class));
    }

    @Test
    void shouldImportSharedFileIntoRecipientWorkspace() {
        User owner = createUser(7L);
        User recipient = createUser(8L);
        FileBlob blob = createBlob(80L, "blobs/blob-import", 5L, "text/plain");
        StoredFile sourceFile = createFile(22L, owner, "/docs", "notes.txt", blob);
        FileShareLink shareLink = new FileShareLink();
        shareLink.setId(100L);
        shareLink.setToken("share-token-1");
        shareLink.setOwner(owner);
        shareLink.setFile(sourceFile);
        shareLink.setCreatedAt(LocalDateTime.now());
        when(fileShareLinkRepository.findByToken("share-token-1")).thenReturn(Optional.of(shareLink));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(8L, "/下载", "notes.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(200L);
            return file;
        });
        FileMetadataResponse response = fileService.importSharedFile(recipient, "share-token-1", "/下载");

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.path()).isEqualTo("/下载");
        assertThat(response.filename()).isEqualTo("notes.txt");
        verify(fileContentStorage, never()).storeImportedFile(any(), any(), any(), any(), any());
        verify(fileContentStorage, never()).readFile(any(), any(), any());
    }

    @Test
    void shouldDeleteImportedBlobWhenMetadataSaveFails() {
        User recipient = createUser(8L);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(8L, "/下载", "notes.txt")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(8L, "/", "下载")).thenReturn(true);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("insert failed")).when(storedFileRepository).save(any(StoredFile.class));

        assertThatThrownBy(() -> fileService.importExternalFile(recipient, "/下载", "notes.txt", "text/plain", content.length, content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert failed");

        verify(fileContentStorage).deleteBlob(org.mockito.ArgumentMatchers.argThat(
                key -> key != null && key.startsWith("blobs/")));
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private FileBlob createBlob(Long id, String objectKey, Long size, String contentType) {
        FileBlob blob = new FileBlob();
        blob.setId(id);
        blob.setObjectKey(objectKey);
        blob.setSize(size);
        blob.setContentType(contentType);
        blob.setCreatedAt(LocalDateTime.now());
        return blob;
    }

    private StoredFile createFile(Long id, User user, String path, String filename) {
        return createFile(id, user, path, filename, createBlob(id, "blobs/blob-" + id, 5L, "text/plain"));
    }

    private StoredFile createFile(Long id, User user, String path, String filename, FileBlob blob) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setFilename(filename);
        file.setPath(path);
        file.setSize(5L);
        file.setDirectory(false);
        file.setContentType("text/plain");
        file.setBlob(blob);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private StoredFile createDirectory(Long id, User user, String path, String filename) {
        StoredFile directory = createFile(id, user, path, filename);
        directory.setDirectory(true);
        directory.setContentType("directory");
        directory.setSize(0L);
        directory.setBlob(null);
        return directory;
    }
}
