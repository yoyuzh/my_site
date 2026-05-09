package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.application.*;
import com.yoyuzh.files.workspace.internal.domain.*;
import com.yoyuzh.files.workspace.internal.infra.*;
import com.yoyuzh.files.content.internal.application.*;
import com.yoyuzh.files.content.internal.domain.*;
import com.yoyuzh.files.content.internal.infra.*;

import com.yoyuzh.ops.admin.internal.application.AdminMetricsService;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.upload.api.InitiateUploadRequest;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
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
import static org.mockito.Mockito.lenient;
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
    private FileBlobRepository fileBlobRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private AdminMetricsService adminMetricsService;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        fileService = FileServiceTestSupport.create(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                adminMetricsService,
                new WorkspaceDownloadOptions(null, null, null, 300L),
                properties.getMaxFileSize()
        );
        lenient().when(fileBlobRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long blobId = invocation.getArgument(0);
            return Optional.of(createBlob(blobId, "blobs/blob-" + blobId, 5L, "text/plain"));
        });
    }

    // --- normalizeDirectoryPath edge cases ---

    @Test
    void shouldRejectPathContainingDotDot() {
        User user = createUser(1L);

        assertThatThrownBy(() -> fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "/docs/../secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径不合法");
    }

    @Test
    void shouldNormalizeBackslashesInPath() {
        User user = createUser(1L);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        // backslash should be treated as path separator and normalized
        FileMetadataResponse response = fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "\\docs");

        assertThat(response.path()).isEqualTo("/");
        assertThat(response.filename()).isEqualTo("docs");
    }

    @Test
    void shouldNormalizeTrailingSlashInPath() {
        User user = createUser(1L);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        FileMetadataResponse response = fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "/docs/");

        assertThat(response.path()).isEqualTo("/");
        assertThat(response.filename()).isEqualTo("docs");
    }

    @Test
    void shouldNormalizeDoubleSlashInPath() {
        User user = createUser(1L);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        FileMetadataResponse response = fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "//docs");

        assertThat(response.path()).isEqualTo("/");
        assertThat(response.filename()).isEqualTo("docs");
    }

    // --- mkdir edge cases ---

    @Test
    void shouldRejectCreatingRootDirectory() {
        User user = createUser(1L);

        assertThatThrownBy(() -> fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "/"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("根目录无需创建");
    }

    @Test
    void shouldAutoRenameCreatingAlreadyExistingDirectory() {
        User user = createUser(1L);
        when(storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(1L, "/", "docs", "docs"))
                .thenReturn(List.of("docs"));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(11L);
            return f;
        });

        FileMetadataResponse response = fileService.mkdir(FileServiceTestSupport.workspaceUser(user), "/docs");

        assertThat(response.filename()).isEqualTo("docs(1)");
        assertThat(response.path()).isEqualTo("/");
    }

    // --- download redirect for direct download ---

    @Test
    void shouldReturn302RedirectWhenStorageSupportsDirectDownloadForFile() {
        User user = createUser(1L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createBlobDownloadUrl("blobs/blob-10", "notes.txt"))
                .thenReturn("https://cdn.example.com/notes.txt");

        WorkspaceDownloadResult response = fileService.download(FileServiceTestSupport.workspaceUser(user), 10L);

        assertThat(response.redirect()).isTrue();
        assertThat(response.redirectUrl()).isEqualTo("https://cdn.example.com/notes.txt");
        verify(adminMetricsService).recordDownloadTraffic(5L);
    }

    // --- getDownloadUrl edge cases ---

    @Test
    void shouldRejectDownloadUrlForDirectory() {
        User user = createUser(1L);
        StoredFile directory = createDirectory(10L, user, "/", "docs");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> fileService.getDownloadUrl(FileServiceTestSupport.workspaceUser(user), 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目录不支持下载");
    }

    // --- upload size limit ---

    @Test
    void shouldRejectUploadExceedingMaxFileSize() {
        User user = createUser(1L);
        long oversizedFile = 500L * 1024 * 1024 + 1;

        assertThatThrownBy(() -> fileService.initiateUpload(FileServiceTestSupport.workspaceUser(user),
                new InitiateUploadRequest("/docs", "big.zip", "application/zip", oversizedFile)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超出限制");
    }

    @Test
    void shouldRejectUploadExceedingUserMaxUploadSizeLimit() {
        User user = createUser(1L);
        user.setMaxUploadSizeBytes(1024L);

        assertThatThrownBy(() -> fileService.initiateUpload(FileServiceTestSupport.workspaceUser(user),
                new InitiateUploadRequest("/docs", "large.bin", "application/octet-stream", 1025L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超出限制");
    }

    @Test
    void shouldRejectUploadWhenUserStorageQuotaInsufficient() {
        User user = createUser(1L);
        user.setStorageQuotaBytes(1024L);
        when(storedFileRepository.sumFileSizeByUserId(1L)).thenReturn(900L);

        assertThatThrownBy(() -> fileService.initiateUpload(FileServiceTestSupport.workspaceUser(user),
                new InitiateUploadRequest("/docs", "quota.bin", "application/octet-stream", 200L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存储空间不足");
    }

    // --- rename no-op when name unchanged ---

    @Test
    void shouldReturnUnchangedFileWhenRenameToSameName() {
        User user = createUser(1L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));

        FileMetadataResponse response = fileService.rename(FileServiceTestSupport.workspaceUser(user), 10L, "notes.txt");

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
        file.setUserId(user.getId());
        file.setFilename(filename);
        file.setPath(path);
        file.setSize(5L);
        file.setDirectory(false);
        file.setContentType("text/plain");
        file.setBlobId(createBlob(id, "blobs/blob-" + id, 5L, "text/plain") == null ? null : createBlob(id, "blobs/blob-" + id, 5L, "text/plain").getId());
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }

    private StoredFile createDirectory(Long id, User user, String path, String filename) {
        StoredFile dir = createFile(id, user, path, filename);
        dir.setDirectory(true);
        dir.setContentType("directory");
        dir.setSize(0L);
        dir.setBlobId(null);
        return dir;
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
}
