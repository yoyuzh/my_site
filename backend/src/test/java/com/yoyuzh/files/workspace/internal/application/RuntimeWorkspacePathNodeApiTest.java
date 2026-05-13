package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspacePathNodeApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldReturnRootPseudoNode() {
        RuntimeWorkspacePathNodeApi api = createApi();

        Optional<WorkspaceFileSnapshot> root = api.findOwnedActiveNodeByPath(7L, "/");

        assertThat(root).isPresent();
        assertThat(root.orElseThrow().userId()).isEqualTo(7L);
        assertThat(root.orElseThrow().filename()).isEqualTo("");
        assertThat(root.orElseThrow().path()).isEqualTo("/");
        assertThat(root.orElseThrow().directory()).isTrue();
    }

    @Test
    void shouldFindOwnedFileByLogicalPath() {
        RuntimeWorkspacePathNodeApi api = createApi();
        StoredFile file = createFile(11L, 7L, "/Docs", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/Docs", "a.txt"))
                .thenReturn(Optional.of(file));

        Optional<WorkspaceFileSnapshot> result = api.findOwnedActiveNodeByPath(7L, "/Docs/a.txt");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().filename()).isEqualTo("a.txt");
        assertThat(result.orElseThrow().path()).isEqualTo("/Docs");
    }

    @Test
    void shouldNotFindOtherUsersFileByPath() {
        RuntimeWorkspacePathNodeApi api = createApi();
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "a.txt"))
                .thenReturn(Optional.empty());

        Optional<WorkspaceFileSnapshot> result = api.findOwnedActiveNodeByPath(7L, "/a.txt");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldListOwnedDirectory() {
        RuntimeWorkspacePathNodeApi api = createApi();
        StoredFile directory = createFile(12L, 7L, "/Docs", "Archive", true);
        StoredFile file = createFile(13L, 7L, "/Docs", "notes.txt", false);
        when(storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                7L,
                "/Docs",
                PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(directory, file)));
        when(storedFileRepository.findDirectoryPathsWithChildDirectories(7L, List.of("/Docs/Archive")))
                .thenReturn(List.of("/Docs/Archive"));

        PageResponse<?> response = api.listOwnedDirectory(7L, "/Docs", 0, 100);

        assertThat(response.items()).hasSize(2);
        assertThat(response.total()).isEqualTo(2L);
    }

    private RuntimeWorkspacePathNodeApi createApi() {
        return new RuntimeWorkspacePathNodeApi(
                storedFileRepository,
                new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage),
                new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage)
        );
    }

    private StoredFile createFile(Long id, Long userId, String path, String filename, boolean directory) {
        StoredFile storedFile = new StoredFile();
        storedFile.setId(id);
        storedFile.setUserId(userId);
        storedFile.setPath(path);
        storedFile.setFilename(filename);
        storedFile.setDirectory(directory);
        storedFile.setContentType(directory ? "directory" : "text/plain");
        storedFile.setSize(directory ? 0L : 5L);
        storedFile.setCreatedAt(LocalDateTime.now());
        return storedFile;
    }
}
