package com.yoyuzh.files.core;

import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCredentialMode;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.share.CreateFileShareLinkResponse;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
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
    private FileEntityRepository fileEntityRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private FileShareLinkRepository fileShareLinkRepository;
    @Mock
    private AdminMetricsService adminMetricsService;
    @Mock
    private StoragePolicyService storagePolicyService;

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
    void shouldAttachPrimaryEntityWhenUploadingFile() {
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

        fileService.upload(user, "/docs", multipartFile);

        var savedFileCaptor = forClass(StoredFile.class);
        verify(storedFileRepository, times(2)).save(savedFileCaptor.capture());
        StoredFile storedFile = savedFileCaptor.getAllValues().stream()
                .filter(file -> !file.isDirectory())
                .findFirst()
                .orElseThrow();
        assertThat(storedFile.getPrimaryEntity()).isNotNull();
        assertThat(storedFile.getPrimaryEntity().getObjectKey()).isEqualTo(storedFile.getBlob().getObjectKey());
        assertThat(storedFile.getPrimaryEntity().getEntityType()).isEqualTo(FileEntityType.VERSION);
        assertThat(storedFile.getPrimaryEntity().getReferenceCount()).isEqualTo(1);
    }

    @Test
    void shouldPersistFileEntityAndRelationWhenUploadingFile() {
        fileService = new FileService(
                storedFileRepository,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                fileContentStorage,
                fileShareLinkRepository,
                adminMetricsService,
                storagePolicyService,
                new FileStorageProperties()
        );
        User user = createUser(7L);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(100L);
            return blob;
        });
        when(fileEntityRepository.findByObjectKeyAndEntityType(org.mockito.ArgumentMatchers.anyString(), eq(FileEntityType.VERSION)))
                .thenReturn(Optional.empty());
        when(fileEntityRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(200L);
            return entity;
        });
        when(storagePolicyService.ensureDefaultPolicy()).thenReturn(createDefaultStoragePolicy());
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile file = invocation.getArgument(0);
            file.setId(10L);
            return file;
        });

        fileService.upload(user, "/docs", multipartFile);

        var entityCaptor = forClass(FileEntity.class);
        verify(fileEntityRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getObjectKey()).startsWith("blobs/");
        assertThat(entityCaptor.getValue().getEntityType()).isEqualTo(FileEntityType.VERSION);
        assertThat(entityCaptor.getValue().getCreatedBy()).isSameAs(user);
        assertThat(entityCaptor.getValue().getStoragePolicyId()).isEqualTo(42L);

        var relationCaptor = forClass(StoredFileEntity.class);
        verify(storedFileEntityRepository).save(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getStoredFile().getId()).isEqualTo(10L);
        assertThat(relationCaptor.getValue().getFileEntity().getId()).isEqualTo(200L);
        assertThat(relationCaptor.getValue().getEntityRole()).isEqualTo("PRIMARY");
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
    void shouldInitiateProxyUploadWhenDefaultPolicyDisablesDirectUpload() {
        fileService = new FileService(
                storedFileRepository,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                fileContentStorage,
                fileShareLinkRepository,
                adminMetricsService,
                storagePolicyService,
                new FileStorageProperties()
        );
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "notes.txt")).thenReturn(false);
        StoragePolicy policy = createDefaultStoragePolicy();
        when(storagePolicyService.ensureDefaultPolicy()).thenReturn(policy);
        when(storagePolicyService.readCapabilities(policy)).thenReturn(new com.yoyuzh.files.policy.StoragePolicyCapabilities(
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                500L * 1024 * 1024
        ));

        InitiateUploadResponse response = fileService.initiateUpload(user,
                new InitiateUploadRequest("/docs", "notes.txt", "text/plain", 12L));

        assertThat(response.direct()).isFalse();
        assertThat(response.storageName()).startsWith("blobs/");
        verify(fileContentStorage, never()).prepareBlobUpload(any(), any(), any(), any(), any(Long.class));
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
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs"))
                .thenReturn(Optional.of(createDirectory(20L, user, "/", "docs")));
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
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs"))
                .thenReturn(Optional.of(createDirectory(21L, user, "/", "docs")));
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
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "projects")).thenReturn(Optional.empty());
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/projects", "site")).thenReturn(Optional.empty());
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
        var copiedFileCaptor = forClass(StoredFile.class);
        verify(storedFileRepository).save(copiedFileCaptor.capture());
        assertThat(copiedFileCaptor.getValue().getPrimaryEntity()).isNotNull();
        assertThat(copiedFileCaptor.getValue().getPrimaryEntity().getObjectKey()).isEqualTo(blob.getObjectKey());
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
    void shouldMoveDeletedDirectoryAndDescendantsIntoRecycleBinGroup() {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile nestedDirectory = createDirectory(12L, user, "/docs/archive", "nested");
        FileBlob blob = createBlob(60L, "blobs/blob-delete", 5L, "text/plain");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt", blob);

        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(nestedDirectory, childFile));

        fileService.delete(user, 10L);

        assertThat(directory.getDeletedAt()).isNotNull();
        assertThat(directory.isRecycleRoot()).isTrue();
        assertThat(directory.getRecycleGroupId()).isNotBlank();
        assertThat(directory.getRecycleOriginalPath()).isEqualTo("/docs");
        assertThat(directory.getPath()).startsWith("/.recycle/");

        assertThat(nestedDirectory.getDeletedAt()).isEqualTo(directory.getDeletedAt());
        assertThat(nestedDirectory.isRecycleRoot()).isFalse();
        assertThat(nestedDirectory.getRecycleGroupId()).isEqualTo(directory.getRecycleGroupId());
        assertThat(nestedDirectory.getRecycleOriginalPath()).isEqualTo("/docs/archive");

        assertThat(childFile.getDeletedAt()).isEqualTo(directory.getDeletedAt());
        assertThat(childFile.isRecycleRoot()).isFalse();
        assertThat(childFile.getRecycleGroupId()).isEqualTo(directory.getRecycleGroupId());
        assertThat(childFile.getRecycleOriginalPath()).isEqualTo("/docs/archive");

        verify(fileContentStorage, never()).deleteBlob(any());
        verify(fileBlobRepository, never()).delete(any());
        verify(storedFileRepository, never()).deleteAll(any());
        verify(storedFileRepository, never()).delete(any());
    }

    @Test
    void shouldKeepSharedBlobWhenFileMovesIntoRecycleBin() {
        User user = createUser(7L);
        FileBlob blob = createBlob(70L, "blobs/blob-shared", 5L, "text/plain");
        StoredFile storedFile = createFile(15L, user, "/docs", "shared.txt", blob);
        when(storedFileRepository.findDetailedById(15L)).thenReturn(Optional.of(storedFile));

        fileService.delete(user, 15L);

        assertThat(storedFile.getDeletedAt()).isNotNull();
        assertThat(storedFile.isRecycleRoot()).isTrue();
        verify(fileContentStorage, never()).deleteBlob(any());
        verify(fileBlobRepository, never()).delete(any());
        verify(storedFileRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteExpiredRecycleBinBlobWhenLastReferenceIsRemoved() {
        User user = createUser(7L);
        FileBlob blob = createBlob(71L, "blobs/blob-last", 5L, "text/plain");
        StoredFile storedFile = createFile(16L, user, "/docs", "last.txt", blob);
        storedFile.setDeletedAt(LocalDateTime.now().minusDays(11));
        storedFile.setRecycleRoot(true);
        storedFile.setRecycleGroupId("recycle-group-1");
        storedFile.setRecycleOriginalPath("/docs");
        storedFile.setPath("/.recycle/recycle-group-1/docs");
        when(storedFileRepository.findByDeletedAtBefore(any(LocalDateTime.class))).thenReturn(List.of(storedFile));
        when(storedFileRepository.countByBlobId(71L)).thenReturn(1L);

        fileService.pruneExpiredRecycleBinItems();

        verify(fileContentStorage).deleteBlob("blobs/blob-last");
        verify(fileBlobRepository).delete(blob);
        verify(storedFileRepository).deleteAll(List.of(storedFile));
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
    void shouldUseDlUrlForPrivateApkWhenConfigured() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        properties.getS3().setPackageDownloadBaseUrl("https://api.yoyuzh.xyz/_dl");
        properties.getS3().setPackageDownloadSecret("test-secret");
        properties.getS3().setPackageDownloadTtlSeconds(300);
        fileService = new FileService(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                fileShareLinkRepository,
                adminMetricsService,
                properties,
                Clock.fixed(Instant.parse("2026-04-04T04:30:00Z"), ZoneOffset.UTC)
        );

        User user = createUser(7L);
        StoredFile file = createFile(22L, user, "/apps", "安装包.apk");
        file.setContentType("application/vnd.android.package-archive");
        when(storedFileRepository.findDetailedById(22L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);

        DownloadUrlResponse response = fileService.getDownloadUrl(user, 22L);

        URI uri = URI.create(response.url());
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("api.yoyuzh.xyz");
        assertThat(uri.getPath()).isEqualTo("/_dl/blobs/blob-22");
        assertThat(response.url()).contains("expires=1775277300");
        assertThat(response.url()).contains("md5=1z0AP88pnPz-TpgnYfIT4A");
        assertThat(response.url()).contains("response-content-disposition=attachment%3B%20filename%3D%22download.apk%22%3B%20filename*%3DUTF-8%27%27%E5%AE%89%E8%A3%85%E5%8C%85.apk");
        verify(fileContentStorage, never()).createBlobDownloadUrl(any(), any());
    }

    @Test
    void shouldRedirectPrivateApkDownloadToDlWhenConfigured() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        properties.getS3().setPackageDownloadBaseUrl("https://api.yoyuzh.xyz/_dl");
        properties.getS3().setPackageDownloadSecret("test-secret");
        properties.getS3().setPackageDownloadTtlSeconds(300);
        fileService = new FileService(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                fileShareLinkRepository,
                adminMetricsService,
                properties,
                Clock.fixed(Instant.parse("2026-04-04T04:30:00Z"), ZoneOffset.UTC)
        );

        User user = createUser(7L);
        StoredFile file = createFile(22L, user, "/apps", "app-debug.apk");
        file.setContentType("application/vnd.android.package-archive");
        when(storedFileRepository.findDetailedById(22L)).thenReturn(Optional.of(file));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);

        ResponseEntity<?> response = fileService.download(user, 22L);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getHost()).isEqualTo("api.yoyuzh.xyz");
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/_dl/blobs/blob-22");
        assertThat(response.getHeaders().getLocation().getQuery()).contains("expires=1775277300");
        assertThat(response.getHeaders().getLocation().getQuery()).contains("md5=1z0AP88pnPz-TpgnYfIT4A");
        verify(fileContentStorage, never()).createBlobDownloadUrl(any(), any());
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
    void shouldBuildZipBytesForDirectoryForBackgroundArchiveReuse() throws Exception {
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childDirectory = createDirectory(11L, user, "/docs/archive", "nested");
        StoredFile childFile = createFile(12L, user, "/docs/archive", "notes.txt");
        StoredFile nestedFile = createFile(13L, user, "/docs/archive/nested", "todo.txt");

        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive"))
                .thenReturn(List.of(childDirectory, childFile, nestedFile));
        when(fileContentStorage.readBlob("blobs/blob-12"))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));
        when(fileContentStorage.readBlob("blobs/blob-13"))
                .thenReturn("world".getBytes(StandardCharsets.UTF_8));

        byte[] archiveBytes = fileService.buildArchiveBytes(directory);

        Map<String, String> entries = readZipEntries(archiveBytes);

        assertThat(entries).containsEntry("archive/", "");
        assertThat(entries).containsEntry("archive/nested/", "");
        assertThat(entries).containsEntry("archive/notes.txt", "hello");
        assertThat(entries).containsEntry("archive/nested/todo.txt", "world");
        verify(fileContentStorage).readBlob("blobs/blob-12");
        verify(fileContentStorage).readBlob("blobs/blob-13");
    }

    @Test
    void shouldBuildZipBytesForSingleFileForBackgroundArchiveReuse() throws Exception {
        User user = createUser(7L);
        StoredFile file = createFile(12L, user, "/docs", "notes.txt");
        when(fileContentStorage.readBlob("blobs/blob-12"))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));

        byte[] archiveBytes = fileService.buildArchiveBytes(file);

        Map<String, String> entries = readZipEntries(archiveBytes);

        assertThat(entries).containsEntry("notes.txt", "hello");
        verify(fileContentStorage).readBlob("blobs/blob-12");
        verify(storedFileRepository, never()).findByUserIdAndPathEqualsOrDescendant(any(), any());
    }

    @Test
    void shouldReadZipCompatibleArchiveForExtractTaskReuse() throws Exception {
        User user = createUser(7L);
        StoredFile archive = createFile(20L, user, "/docs", "extract.zip", createBlob(20L, "blobs/blob-20", 64L, "application/zip"));
        when(fileContentStorage.readBlob("blobs/blob-20")).thenReturn(createZipArchive(Map.of(
                "archive/", "",
                "archive/nested/", "",
                "archive/notes.txt", "hello",
                "archive/nested/todo.txt", "world"
        )));

        FileService.ZipCompatibleArchive zipArchive = fileService.readZipCompatibleArchive(archive);

        assertThat(zipArchive.commonRootDirectoryName()).isEqualTo("archive");
        assertThat(zipArchive.entries())
                .extracting(FileService.ZipCompatibleArchiveEntry::relativePath, FileService.ZipCompatibleArchiveEntry::directory)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("archive", true),
                        org.assertj.core.groups.Tuple.tuple("archive/nested", true),
                        org.assertj.core.groups.Tuple.tuple("archive/notes.txt", false),
                        org.assertj.core.groups.Tuple.tuple("archive/nested/todo.txt", false)
                );
        Map<String, String> fileEntries = zipArchive.entries().stream()
                .filter(entry -> !entry.directory())
                .collect(java.util.stream.Collectors.toMap(
                        FileService.ZipCompatibleArchiveEntry::relativePath,
                        entry -> new String(entry.content(), StandardCharsets.UTF_8),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        assertThat(fileEntries)
                .containsEntry("archive/notes.txt", "hello")
                .containsEntry("archive/nested/todo.txt", "world");
        verify(fileContentStorage).readBlob("blobs/blob-20");
    }

    @Test
    void shouldRejectZipCompatibleArchiveWithTraversalEntry() throws Exception {
        User user = createUser(7L);
        StoredFile archive = createFile(21L, user, "/docs", "extract.zip", createBlob(21L, "blobs/blob-21", 32L, "application/zip"));
        when(fileContentStorage.readBlob("blobs/blob-21")).thenReturn(createZipArchive(Map.of(
                "../evil.txt", "oops"
        )));

        assertThatThrownBy(() -> fileService.readZipCompatibleArchive(archive))
                .isInstanceOf(BusinessException.class)
                .hasMessage("压缩包内容不合法");
    }

    @Test
    void shouldDeleteWrittenBlobsWhenBatchExternalImportFails() {
        User user = createUser(8L);
        StoredFile docs = createDirectory(300L, user, "/", "docs");
        when(storedFileRepository.findByUserIdAndPathAndFilename(8L, "/", "docs")).thenReturn(Optional.of(docs));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(8L, "/docs", "first.txt")).thenReturn(false);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(8L, "/docs", "second.txt")).thenReturn(false);
        when(fileBlobRepository.save(any(FileBlob.class))).thenAnswer(invocation -> {
            FileBlob blob = invocation.getArgument(0);
            blob.setId(System.nanoTime());
            return blob;
        });
        when(storedFileRepository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> {
                    StoredFile file = invocation.getArgument(0);
                    if ("second.txt".equals(file.getFilename())) {
                        throw new BusinessException(com.yoyuzh.common.ErrorCode.UNKNOWN, "metadata save failed");
                    }
                    file.setId(400L);
                    return file;
                });

        assertThatThrownBy(() -> fileService.importExternalFilesAtomically(
                user,
                List.of(),
                List.of(
                        new FileService.ExternalFileImport("/docs", "first.txt", "text/plain", "first".getBytes(StandardCharsets.UTF_8)),
                        new FileService.ExternalFileImport("/docs", "second.txt", "text/plain", "second".getBytes(StandardCharsets.UTF_8))
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessage("metadata save failed");

        var objectKeyCaptor = forClass(String.class);
        verify(fileContentStorage, times(2)).storeBlob(objectKeyCaptor.capture(), eq("text/plain"), any(byte[].class));
        List<String> writtenKeys = objectKeyCaptor.getAllValues();
        assertThat(writtenKeys).hasSize(2);
        verify(fileContentStorage).deleteBlob(writtenKeys.get(0));
        verify(fileContentStorage).deleteBlob(writtenKeys.get(1));
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
        when(storedFileRepository.findByUserIdAndPathAndFilename(8L, "/", "下载"))
                .thenReturn(Optional.of(createDirectory(22L, recipient, "/", "下载")));
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

    private StoragePolicy createDefaultStoragePolicy() {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(42L);
        policy.setName("Default Local Storage");
        policy.setType(StoragePolicyType.LOCAL);
        policy.setCredentialMode(StoragePolicyCredentialMode.NONE);
        policy.setMaxSizeBytes(500L * 1024 * 1024);
        policy.setEnabled(true);
        policy.setDefaultPolicy(true);
        return policy;
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

    private byte[] createZipArchive(Map<String, String> entries) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Set<String> createdEntries = new java.util.LinkedHashSet<>();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                if (!createdEntries.add(entry.getKey())) {
                    continue;
                }
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

}
