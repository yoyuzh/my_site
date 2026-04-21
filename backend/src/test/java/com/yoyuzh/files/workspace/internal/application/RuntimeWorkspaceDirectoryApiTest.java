package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
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
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/", "docs")).thenReturn(false);
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
        verify(fileContentStorage).createDirectory(7L, "/docs");
    }

    @Test
    void shouldLoadDirectoryPage() {
        RuntimeWorkspaceDirectoryApi api = new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage);
        StoredFile storedFile = new StoredFile();
        storedFile.setId(12L);
        storedFile.setFilename("notes.txt");
        storedFile.setPath("/docs");
        storedFile.setSize(5L);
        storedFile.setContentType("text/plain");
        storedFile.setDirectory(false);
        storedFile.setCreatedAt(LocalDateTime.now());
        when(storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(7L, "/docs", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(storedFile)));

        PageResponse<FileMetadataResponse> response = api.loadDirectoryPage(7L, "/docs", 0, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).filename()).isEqualTo("notes.txt");
        assertThat(response.total()).isEqualTo(1L);
    }

}
