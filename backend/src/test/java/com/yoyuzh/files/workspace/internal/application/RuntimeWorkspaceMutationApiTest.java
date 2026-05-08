package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMoveOutcomeStatus;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.files.workspace.api.WorkspaceMutationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceMutationApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldRenameDirectoryAndUpdateDescendantPaths() {
        RuntimeWorkspaceMutationApi api = new RuntimeWorkspaceMutationApi(storedFileRepository, fileContentStorage);
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile childFile = createFile(11L, user, "/docs/archive", "nested.txt");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "renamed-archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceMutationResult result = api.rename(user.getId(), 10L, "renamed-archive");

        assertThat(result.file().filename()).isEqualTo("renamed-archive");
        assertThat(result.fromPath()).isEqualTo("/docs/archive");
        assertThat(result.toPath()).isEqualTo("/docs/renamed-archive");
        assertThat(childFile.getPath()).isEqualTo("/docs/renamed-archive");
        assertThat(result.affectedPaths()).containsExactly("/docs");
    }

    @Test
    void shouldMoveDirectoryAndUpdateDescendantPaths() {
        RuntimeWorkspaceMutationApi api = new RuntimeWorkspaceMutationApi(storedFileRepository, fileContentStorage);
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "图片");
        StoredFile childFile = createFile(12L, user, "/docs/archive", "nested.txt");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        stubExistingNodes(7L, targetDirectory);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片", "archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceMoveResult result = api.move(user.getId(), 10L, "/图片", null);

        assertThat(result.status()).isEqualTo(WorkspaceMoveOutcomeStatus.SUCCESS);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).toPath()).isEqualTo("/图片/archive");
        assertThat(childFile.getPath()).isEqualTo("/图片/archive");
    }

    @Test
    void shouldRejectMovingDirectoryIntoItsOwnDescendant() {
        RuntimeWorkspaceMutationApi api = new RuntimeWorkspaceMutationApi(storedFileRepository, fileContentStorage);
        User user = createUser(7L);
        StoredFile directory = createDirectory(10L, user, "/docs", "archive");
        StoredFile docsDirectory = createDirectory(11L, user, "/", "docs");
        StoredFile archiveDirectory = createDirectory(12L, user, "/docs", "archive");
        StoredFile descendantDirectory = createDirectory(13L, user, "/docs/archive", "nested");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(directory));
        stubExistingNodes(7L, docsDirectory, archiveDirectory, descendantDirectory);

        WorkspaceMoveResult result = api.move(user.getId(), 10L, "/docs/archive/nested", null);

        assertThat(result.status()).isEqualTo(WorkspaceMoveOutcomeStatus.INVALID_TARGET);
        assertThat(result.message()).contains("不能移动到当前目录或其子目录");
    }

    @Test
    void shouldReturnConflictWhenTargetAlreadyHasSameNameWithoutStrategy() {
        RuntimeWorkspaceMutationApi api = new RuntimeWorkspaceMutationApi(storedFileRepository, fileContentStorage);
        User user = createUser(7L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "下载");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));
        stubExistingNodes(7L, targetDirectory);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/下载", "notes.txt")).thenReturn(true);

        WorkspaceMoveResult result = api.move(user.getId(), 10L, "/下载", null);

        assertThat(result.status()).isEqualTo(WorkspaceMoveOutcomeStatus.CONFLICT);
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).toPath()).isEqualTo("/下载/notes.txt");
    }

    @Test
    void shouldAutoRenameMovedFileWhenStrategyRequestsIt() {
        RuntimeWorkspaceMutationApi api = new RuntimeWorkspaceMutationApi(storedFileRepository, fileContentStorage);
        User user = createUser(7L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt");
        StoredFile targetDirectory = createDirectory(11L, user, "/", "下载");
        when(storedFileRepository.findDetailedById(10L)).thenReturn(Optional.of(file));
        stubExistingNodes(7L, targetDirectory);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/下载", "notes.txt")).thenReturn(true);
        when(storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(7L, "/下载", "notes.txt", "notes"))
                .thenReturn(List.of("notes.txt", "notes(1).txt"));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceMoveResult result = api.move(user.getId(), 10L, "/下载", WorkspaceMoveConflictStrategy.AUTO_RENAME);

        assertThat(result.status()).isEqualTo(WorkspaceMoveOutcomeStatus.SUCCESS);
        assertThat(result.items().get(0).renamed()).isTrue();
        assertThat(result.items().get(0).toPath()).isEqualTo("/下载/notes(2).txt");
        assertThat(file.getFilename()).isEqualTo("notes(2).txt");
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

    private StoredFile createFile(Long id, User user, String path, String filename) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setUserId(user.getId());
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setDirectory(false);
        storedFile.setContentType("text/plain");
        storedFile.setSize(5L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }

    private void stubExistingNodes(Long userId, StoredFile... files) {
        when(storedFileRepository.findActiveNodesByUserIdAndPathInAndFilenameIn(eq(userId), any(), any()))
                .thenReturn(List.of(files));
    }
}
