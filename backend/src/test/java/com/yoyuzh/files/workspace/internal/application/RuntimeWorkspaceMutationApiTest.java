package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
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

        WorkspaceMutationResult result = api.rename(user, 10L, "renamed-archive");

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
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "图片")).thenReturn(Optional.of(targetDirectory));
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/图片", "archive")).thenReturn(false);
        when(storedFileRepository.findByUserIdAndPathEqualsOrDescendant(7L, "/docs/archive")).thenReturn(List.of(childFile));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceMutationResult result = api.move(user, 10L, "/图片");

        assertThat(result.file().path()).isEqualTo("/图片/archive");
        assertThat(result.fromPath()).isEqualTo("/docs/archive");
        assertThat(result.toPath()).isEqualTo("/图片/archive");
        assertThat(childFile.getPath()).isEqualTo("/图片/archive");
        assertThat(result.affectedPaths()).containsExactly("/docs", "/图片");
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
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(Optional.of(docsDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs", "archive")).thenReturn(Optional.of(archiveDirectory));
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/docs/archive", "nested")).thenReturn(Optional.of(descendantDirectory));

        assertThatThrownBy(() -> api.move(user, 10L, "/docs/archive/nested"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能移动到当前目录或其子目录");
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
        storedFile.setUser(user);
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
        storedFile.setUser(user);
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setDirectory(false);
        storedFile.setContentType("text/plain");
        storedFile.setSize(5L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }
}
