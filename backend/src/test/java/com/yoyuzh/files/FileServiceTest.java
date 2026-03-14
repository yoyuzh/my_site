package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    private FileService fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setRootDir(tempDir.toString());
        properties.setMaxFileSize(50 * 1024 * 1024);
        fileService = new FileService(storedFileRepository, properties);
    }

    @Test
    void shouldStoreUploadedFileUnderUserDirectory() {
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
        assertThat(response.directory()).isFalse();
        assertThat(tempDir.resolve("7/docs/notes.txt")).exists();
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

        assertThat(tempDir.resolve("7/下载")).exists();
        assertThat(tempDir.resolve("7/文档")).exists();
        assertThat(tempDir.resolve("7/图片")).exists();
        verify(storedFileRepository).existsByUserIdAndPathAndFilename(7L, "/", "下载");
        verify(storedFileRepository).existsByUserIdAndPathAndFilename(7L, "/", "文档");
        verify(storedFileRepository).existsByUserIdAndPathAndFilename(7L, "/", "图片");
        verify(storedFileRepository, times(3)).save(any(StoredFile.class));
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
}
