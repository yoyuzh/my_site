package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceDirectoryApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Test
    void shouldCreateDirectoryEntry() {
        RuntimeWorkspaceDirectoryApi api = new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(10L);
            return storedFile;
        });

        FileMetadataResponse response = api.createDirectory(7L, "/docs");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.filename()).isEqualTo("docs");
        assertThat(response.path()).isEqualTo("/");
        assertThat(response.directory()).isTrue();
        assertThat(response.hasChildDirectory()).isFalse();
        verify(fileContentStorage).createDirectory(7L, "/docs");
    }

    @Test
    void shouldLoadDirectoryPageWithChildDirectoryFlags() {
        RuntimeWorkspaceDirectoryApi api = new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage);
        StoredFile directory = new StoredFile();
        directory.setId(11L);
        directory.setFilename("reports");
        directory.setPath("/docs");
        directory.setSize(0L);
        directory.setContentType("directory");
        directory.setDirectory(true);
        directory.setCreatedAt(LocalDateTime.now());
        StoredFile storedFile = new StoredFile();
        storedFile.setId(12L);
        storedFile.setFilename("notes.txt");
        storedFile.setPath("/docs");
        storedFile.setSize(5L);
        storedFile.setContentType("text/plain");
        storedFile.setDirectory(false);
        storedFile.setCreatedAt(LocalDateTime.now());
        when(storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(7L, "/docs", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(directory, storedFile)));
        when(storedFileRepository.findDirectoryPathsWithChildDirectories(7L, List.of("/docs/reports")))
                .thenReturn(List.of("/docs/reports"));

        PageResponse<FileMetadataResponse> response = api.loadDirectoryPage(7L, "/docs", 0, 10);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).filename()).isEqualTo("reports");
        assertThat(response.items().get(0).hasChildDirectory()).isTrue();
        assertThat(response.items().get(1).filename()).isEqualTo("notes.txt");
        assertThat(response.items().get(1).hasChildDirectory()).isFalse();
        assertThat(response.total()).isEqualTo(2L);
    }

    @Test
    void shouldCreateDirectoryWithAutoRenamedNameWhenSameDirectoryExists() {
        RuntimeWorkspaceDirectoryApi api = new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage);
        when(storedFileRepository.findActiveFilenamesByUserIdAndPathAndFilenamePrefix(7L, "/", "docs", "docs"))
                .thenReturn(List.of("docs"));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile storedFile = invocation.getArgument(0);
            storedFile.setId(11L);
            return storedFile;
        });

        FileMetadataResponse response = api.createDirectory(7L, "/docs");

        assertThat(response.filename()).isEqualTo("docs(1)");
        verify(fileContentStorage).createDirectory(7L, "/docs(1)");
    }

}
