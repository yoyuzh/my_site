package com.yoyuzh.files;

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
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        fileService = new FileService(storedFileRepository, fileContentStorage, properties);
    }

    @Test
    void shouldStoreUploadedFileViaConfiguredStorage() {
        User user = createUser(7L);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(10L);
            return file;
        });

        FileMetadataResponse response = fileService.upload(user, "/docs", multipartFile);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.path()).isEqualTo("/docs");
        verify(fileContentStorage).upload(7L, "/docs", "notes.txt", multipartFile);
    }

    @Test
    void shouldInitiateDirectUploadThroughStorage() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(fileContentStorage.prepareUpload(7L, "/docs", "notes.txt", "text/plain", 12L))
                .thenReturn(new PreparedUpload(true, "https://upload.example.com", "PUT", Map.of("Content-Type", "text/plain"), "notes.txt"));

        InitiateUploadResponse response = fileService.initiateUpload(user,
                new InitiateUploadRequest("/docs", "notes.txt", "text/plain", 12L));

        assertThat(response.direct()).isTrue();
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com");
        verify(fileContentStorage).prepareUpload(7L, "/docs", "notes.txt", "text/plain", 12L);
    }

    @Test
    void shouldAllowInitiatingUploadAtFiveHundredMegabytes() {
        User user = createUser(7L);
        long uploadSize = 500L * 1024 * 1024;
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "movie.zip")).thenReturn(false);
        when(fileContentStorage.prepareUpload(7L, "/docs", "movie.zip", "application/zip", uploadSize))
                .thenReturn(new PreparedUpload(true, "https://upload.example.com", "PUT", Map.of(), "movie.zip"));

        InitiateUploadResponse response = fileService.initiateUpload(user,
                new InitiateUploadRequest("/docs", "movie.zip", "application/zip", uploadSize));

        assertThat(response.direct()).isTrue();
        verify(fileContentStorage).prepareUpload(7L, "/docs", "movie.zip", "application/zip", uploadSize);
    }

    @Test
    void shouldCompleteDirectUploadAndPersistMetadata() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(11L);
            return file;
        });

        FileMetadataResponse response = fileService.completeUpload(user,
                new CompleteUploadRequest("/docs", "notes.txt", "notes.txt", "text/plain", 12L));

        assertThat(response.id()).isEqualTo(11L);
        verify(fileContentStorage).completeUpload(7L, "/docs", "notes.txt", "text/plain", 12L);
    }

    @Test
    void shouldRenameFileThroughConfiguredStorage() {
        User user = createUser(7L);
        StoredFile storedFile = createFile(10L, user, "/docs", "notes.txt");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(storedFile));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "renamed.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.rename(user, 10L, "renamed.txt");

        assertThat(response.filename()).isEqualTo("renamed.txt");
        verify(fileContentStorage).renameFile(7L, "/docs", "notes.txt", "renamed.txt");
    }

    @Test
    void shouldRenameDirectoryAndUpdateDescendantPaths() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt");

        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "renamed-archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.rename(user, 10L, "renamed-archive");

        assertThat(response.filename()).isEqualTo("renamed-archive");
        assertThat(childFile.getPath()).isEqualTo("/docs/renamed-archive");
        verify(fileContentStorage).renameDirectory(7L, "/docs/archive", "/docs/renamed-archive", List.of(childFile));
    }

    @Test
    void shouldRejectDeletingOtherUsersFile() {
        User owner = createUser(1L);
        User requester = createUser(2L);
        StoredFile storedFile = createFile(100L, owner, "/docs", "notes.txt");
        when(storedFileRepository.findById(100L)).thenReturn(Optional.of(storedFile));

        assertThatThrownBy(() -> fileService.delete(requester, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有权限");
    }

    @Test
    void shouldDeleteDirectoryWithNestedFilesViaStorage() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt");

        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));

        fileService.delete(user, 10L);

        verify(fileContentStorage).deleteDirectory(7L, "/docs/archive", List.of(childFile));
        verify(storedFileRepository).deleteAll(List.of(childFile));
        verify(storedFileRepository).delete(directory);
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
        when(storedFileRepository.findById(22L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createDownloadUrl(7L, "/docs", "notes.txt", "notes.txt"))
                .thenReturn("https://download.example.com/file");

        DownloadUrlResponse response = fileService.getDownloadUrl(user, 22L);

        assertThat(response.url()).isEqualTo("https://download.example.com/file");
    }

    @Test
    void shouldFallbackToBackendDownloadUrlWhenStorageIsLocal() {
        User user = createUser(7L);
        StoredFile file = createFile(22L, user, "/docs", "notes.txt");
        when(storedFileRepository.findById(22L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(false);

        DownloadUrlResponse response = fileService.getDownloadUrl(user, 22L);

        assertThat(response.url()).isEqualTo("/api/files/download/22");
        verify(fileContentStorage, never()).createDownloadUrl(any(), any(), any(), any());
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

    private StoredFile createFile(Long id, User user, String path, String filename) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setFilename(filename);
        file.setPath(path);
        file.setSize(5L);
        file.setDirectory(false);
        file.setStorageName(filename);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private StoredFile createDirectory(Long id, User user, String path, String filename) {
        StoredFile directory = createFile(id, user, path, filename);
        directory.setDirectory(true);
        directory.setContentType("directory");
        directory.setSize(0L);
        return directory;
    }
}
