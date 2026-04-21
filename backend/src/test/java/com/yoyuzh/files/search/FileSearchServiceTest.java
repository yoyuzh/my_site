package com.yoyuzh.files.search;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileSearchServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    private FileSearchService fileSearchService;

    @BeforeEach
    void setUp() {
        fileSearchService = new FileSearchService(storedFileRepository);
    }

    @Test
    void shouldSearchOwnedActiveFiles() {
        User user = createUser(7L);
        StoredFile file = createFile(10L, user, "/docs", "notes.txt", false);
        LocalDateTime createdGte = LocalDateTime.of(2026, 4, 8, 8, 0);
        LocalDateTime createdLte = LocalDateTime.of(2026, 4, 8, 12, 0);
        LocalDateTime updatedGte = LocalDateTime.of(2026, 4, 8, 9, 0);
        LocalDateTime updatedLte = LocalDateTime.of(2026, 4, 8, 18, 0);
        when(storedFileRepository.searchUserFiles(
                eq(7L),
                eq("note"),
                eq(false),
                eq(1L),
                eq(100L),
                eq(createdGte),
                eq(createdLte),
                eq(updatedGte),
                eq(updatedLte),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(file), PageRequest.of(0, 20), 1));

        var response = fileSearchService.search(user, new FileSearchQuery(
                " note ",
                false,
                1L,
                100L,
                createdGte,
                createdLte,
                updatedGte,
                updatedLte,
                0,
                20
        ));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).filename()).isEqualTo("notes.txt");
        assertThat(response.items().get(0).path()).isEqualTo("/docs");
    }

    @Test
    void shouldReturnDirectoryLogicalPathForDirectoryResults() {
        User user = createUser(7L);
        StoredFile directory = createFile(11L, user, "/docs", "archive", true);
        when(storedFileRepository.searchUserFiles(
                eq(7L),
                eq(null),
                eq(true),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(directory), PageRequest.of(0, 20), 1));

        var response = fileSearchService.search(user, new FileSearchQuery(
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        ));

        assertThat(response.items().get(0).path()).isEqualTo("/docs/archive");
    }

    @Test
    void shouldRejectInvalidSearchRange() {
        User user = createUser(7L);

        assertThatThrownBy(() -> fileSearchService.search(user, new FileSearchQuery(
                null,
                null,
                100L,
                1L,
                null,
                null,
                null,
                null,
                0,
                20
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小范围不合法");
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        return user;
    }

    private StoredFile createFile(Long id, User user, String path, String filename, boolean directory) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setFilename(filename);
        file.setPath(path);
        file.setContentType(directory ? "directory" : "text/plain");
        file.setSize(directory ? 0L : 5L);
        file.setDirectory(directory);
        file.setCreatedAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        file.setUpdatedAt(LocalDateTime.of(2026, 4, 8, 11, 0));
        return file;
    }
}
