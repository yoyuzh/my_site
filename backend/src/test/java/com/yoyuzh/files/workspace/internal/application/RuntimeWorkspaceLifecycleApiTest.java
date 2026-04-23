package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceLifecycleApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private ContentDuplicationApi contentDuplicationApi;
    @Mock
    private ContentBlobQueryApi contentBlobQueryApi;

    @Test
    void shouldCopyDirectoryAndDescendants() {
        RuntimeWorkspaceLifecycleApi api = new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                fileContentStorage,
                contentDuplicationApi,
                contentBlobQueryApi
        );
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "图片");
        FileBlob childBlob = createBlob(51L, "blobs/blob-archive-1");
        StoredFile childFile = createFile(13L, user, "/docs/archive", "notes.txt", childBlob);
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "图片")).thenReturn(Optional.of(targetDirectory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片", "archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive"))
                .thenReturn(List.of(childFile));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片/archive", "notes.txt")).thenReturn(false);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            if (storedFile.getId() == null) {
                storedFile.setId(100L + storedFile.getFilename().length());
            }
            return storedFile;
        });
        when(contentBlobQueryApi.findBlobReferenceById(51L))
                .thenReturn(Optional.of(new ContentBlobReference(51L, "blobs/blob-archive-1", "text/plain", 5L)));
        when(contentDuplicationApi.duplicateBlobBackedFile(any(ContentRegistrationCommand.class)))
                .thenReturn(new RegisteredContentFile(120L, "notes.txt", "/图片/archive", 5L, "text/plain", false, LocalDateTime.now()));
        AtomicLong guardedBytes = new AtomicLong(-1L);

        WorkspaceLifecycleResult result = api.copy(user.getId(), 10L, "/图片", guardedBytes::set);

        assertThat(result.file().path()).isEqualTo("/图片/archive");
        assertThat(result.toPath()).isEqualTo("/图片/archive");
        assertThat(guardedBytes.get()).isEqualTo(5L);
    }

    @Test
    void shouldRecycleDirectoryTree() {
        RuntimeWorkspaceLifecycleApi api = new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                fileContentStorage,
                contentDuplicationApi,
                contentBlobQueryApi
        );
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt", createBlob(60L, "blobs/blob-delete"));

        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));

        WorkspaceLifecycleResult result = api.recycle(user.getId(), 10L);

        assertThat(directory.getDeletedAt()).isNotNull();
        assertThat(directory.isRecycleRoot()).isTrue();
        assertThat(directory.getPath()).startsWith("/.recycle/");
        assertThat(childFile.getRecycleGroupId()).isEqualTo(directory.getRecycleGroupId());
        assertThat(result.fromPath()).isEqualTo("/docs/archive");
    }

    @Test
    void shouldRestoreRecycleGroup() {
        RuntimeWorkspaceLifecycleApi api = new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                fileContentStorage,
                contentDuplicationApi,
                contentBlobQueryApi
        );
        User user = createUser(7L);
        StoredFile docsDirectory = createDirectory(20L, user, "/", "docs");
        StoredFile recycleRoot = createFile(16L, user, "/.recycle/recycle-group-1/docs", "last.txt", createBlob(71L, "blobs/blob-last"));
        recycleRoot.setDeletedAt(LocalDateTime.now().minusDays(1));
        recycleRoot.setRecycleRoot(true);
        recycleRoot.setRecycleGroupId("recycle-group-1");
        recycleRoot.setRecycleOriginalPath("/docs");
        when(storedFileRepository.findDetailedById(16L)).thenReturn(Optional.of(recycleRoot));
        when(storedFileRepository.findByRecycleGroupId("recycle-group-1")).thenReturn(List.of(recycleRoot));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "last.txt")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(Optional.of(docsDirectory));

        WorkspaceLifecycleResult result = api.restore(user.getId(), 16L, bytes -> assertThat(bytes).isEqualTo(5L));

        assertThat(recycleRoot.getDeletedAt()).isNull();
        assertThat(recycleRoot.getPath()).isEqualTo("/docs");
        assertThat(result.toPath()).isEqualTo("/docs/last.txt");
        verify(storedFileRepository).saveAll(List.of(recycleRoot));
    }

    @Test
    void shouldRejectCopyingDirectoryIntoItsOwnDescendant() {
        RuntimeWorkspaceLifecycleApi api = new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                fileContentStorage,
                contentDuplicationApi,
                contentBlobQueryApi
        );
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile docsDirectory = createDirectory(11L, user, "/", "docs");
        StoredFile archiveDirectory = createDirectory(12L, user, "/docs", "archive");
        StoredFile descendantDirectory = createDirectory(13L, user, "/docs/archive", "nested");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(Optional.of(docsDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs", "archive")).thenReturn(Optional.of(archiveDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs/archive", "nested")).thenReturn(Optional.of(descendantDirectory));

        assertThatThrownBy(() -> api.copy(user.getId(), 10L, "/docs/archive/nested", bytes -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能复制到当前目录或其子目录");
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoredFile createDirectory(Long id, User user, String path, String filename) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setUserId(user.getId());
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setDirectory(true);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }

    private StoredFile createFile(Long id, User user, String path, String filename, FileBlob blob) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setUserId(user.getId());
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setBlobId(blob == null ? null : blob.getId());
        storedFile.setDirectory(false);
        storedFile.setContentType("text/plain");
        storedFile.setSize(5L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }

    private FileBlob createBlob(Long id, String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setId(id);
        blob.setObjectKey(objectKey);
        blob.setContentType("text/plain");
        blob.setSize(5L);
        return blob;
    }
}
