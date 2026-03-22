package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers edge cases not addressed in FileServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceEdgeCaseTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private FileShareLinkRepository fileShareLinkRepository;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        fileService = new FileService(storedFileRepository, fileContentStorage, fileShareLinkRepository, properties);
    }

    // --- normalizeDirectoryPath edge cases ---

    @Test
    void shouldRejectPathContainingDotDot() {
        User user = createUser(1L);

        assertThatThrownBy(() -> fileService.mkdir(user, "/docs/../secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径不合法");
    }

    @Test
    void shouldNormalizeBackslashesInPath() {
        User user = createUser(1L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(1L, "/", "docs")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        // backslash should be treated as path separator and normalized
        FileMetadataResponse response = fileService.mkdir(user, "\\docs");

        assertThat(response.path()).isEqualTo("/docs");
    }

    @Test
    void shouldNormalizeTrailingSlashInPath() {
        User user = createUser(1L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(1L, "/", "docs")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        FileMetadataResponse response = fileService.mkdir(user, "/docs/");

        assertThat(response.path()).isEqualTo("/docs");
    }

    @Test
    void shouldNormalizeDoubleSlashInPath() {
        User user = createUser(1L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(1L, "/", "docs")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        FileMetadataResponse response = fileService.mkdir(user, "//docs");

        assertThat(response.path()).isEqualTo("/docs");
    }

    // --- mkdir edge cases ---

    @Test
    void shouldRejectCreatingRootDirectory() {
        User user = createUser(1L);

        assertThatThrownBy(() -> fileService.mkdir(user, "/"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("根目录无需创建");
    }

    @Test
    void shouldRejectCreatingAlreadyExistingDirectory() {
        User user = createUser(1L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(1L, "/", "docs")).thenReturn(true);

        assertThatThrownBy(() -> fileService.mkdir(user, "/docs"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目录已存在");
    }

    // --- download redirect for direct download ---

    @Test
    void shouldReturn302RedirectWhenStorageSupportsDirectDownloadForFile() {
        User user = createUser(1L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createDownloadUrl(1L, "/docs", "notes.txt", "notes.txt"))
                .thenReturn("https://cdn.example.com/notes.txt");

        ResponseEntity<?> response = fileService.download(user, 10L);

        assertThat(response.getStatusCodeValue()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("https://cdn.example.com/notes.txt");
    }

    // --- createShareLink edge cases ---

    @Test
    void shouldRejectCreatingShareLinkForDirectory() {
        User user = createUser(1L);
        StoredFile directory = createDirectory(10L, user, "/", "docs");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> fileService.createShareLink(user, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目录暂不支持分享链接");
    }

    // --- getDownloadUrl edge cases ---

    @Test
    void shouldRejectDownloadUrlForDirectory() {
        User user = createUser(1L);
        StoredFile directory = createDirectory(10L, user, "/", "docs");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> fileService.getDownloadUrl(user, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目录不支持下载");
    }

    // --- upload size limit ---

    @Test
    void shouldRejectUploadExceedingMaxFileSize() {
        User user = createUser(1L);
        long oversizedFile = 500L * 1024 * 1024 + 1;

        assertThatThrownBy(() -> fileService.initiateUpload(user,
                new InitiateUploadRequest("/docs", "big.zip", "application/zip", oversizedFile)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超出限制");
    }

    // --- rename no-op when name unchanged ---

    @Test
    void shouldReturnUnchangedFileWhenRenameToSameName() {
        User user = createUser(1L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(file));

        FileMetadataResponse response = fileService.rename(user, 10L, "notes.txt");

        assertThat(response.filename()).isEqualTo("notes.txt");
        verify(storedFileRepository, org.mockito.Mockito.never()).save(any());
    }

    // --- helpers ---

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
        file.setStorageName(filename);
        file.setSize(5L);
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private StoredFile createDirectory(Long id, User user, String path, String filename) {
        StoredFile dir = createFile(id, user, path, filename);
        dir.setDirectory(true);
        dir.setContentType("directory");
        dir.setSize(0L);
        return dir;
    }
}
