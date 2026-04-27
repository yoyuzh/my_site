package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchQuery;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspaceFileSearchApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    private RuntimeWorkspaceFileSearchApi api;

    @BeforeEach
    void setUp() {
        api = new RuntimeWorkspaceFileSearchApi(storedFileRepository);
    }

    @Test
    void shouldDelegateCategoryToRepositoryAndMapFiles() {
        StoredFile file = StoredFile.blobBackedFile(7L, "/docs", "report.pdf", "application/pdf", 32L, 10L, "report.pdf", 90L);
        file.setId(11L);
        file.setCreatedAt(LocalDateTime.of(2026, 4, 26, 10, 0));
        file.setUpdatedAt(LocalDateTime.of(2026, 4, 26, 11, 0));
        when(storedFileRepository.searchUserFiles(
                eq(7L),
                eq("report"),
                eq("document"),
                eq(false),
                eq(1L),
                eq(64L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(file), PageRequest.of(0, 20), 1));

        PageResponse<FileMetadataResponse> response = api.search(7L, new WorkspaceFileSearchQuery(
                "report",
                "document",
                false,
                1L,
                64L,
                null,
                null,
                null,
                null,
                0,
                20
        ));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).filename()).isEqualTo("report.pdf");
        ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(storedFileRepository).searchUserFiles(
                eq(7L),
                eq("report"),
                eq("document"),
                eq(false),
                eq(1L),
                eq(64L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }
}
