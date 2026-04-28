package com.yoyuzh.files.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSearchServiceTest {

    @Mock
    private FileSearchApi fileSearchApi;

    private FileSearchService fileSearchService;

    @BeforeEach
    void setUp() {
        fileSearchService = new FileSearchService(fileSearchApi);
    }

    @Test
    void shouldDelegateSearchThroughApiContract() {
        LocalDateTime createdGte = LocalDateTime.of(2026, 4, 8, 8, 0);
        LocalDateTime createdLte = LocalDateTime.of(2026, 4, 8, 12, 0);
        LocalDateTime updatedGte = LocalDateTime.of(2026, 4, 8, 9, 0);
        LocalDateTime updatedLte = LocalDateTime.of(2026, 4, 8, 18, 0);
        PageResponse<FileMetadataResponse> expected = new PageResponse<>(
                List.of(new FileMetadataResponse(
                        10L,
                        "notes.txt",
                        "/docs",
                        5L,
                        "text/plain",
                        false,
                        LocalDateTime.of(2026, 4, 8, 10, 0),
                        LocalDateTime.of(2026, 4, 8, 10, 0),
                        null,
                        null,
                        false
                )),
                1,
                0,
                20
        );
        when(fileSearchApi.search(7L, new SearchFilesQuery(
                " note ",
                null,
                false,
                1L,
                100L,
                createdGte,
                createdLte,
                updatedGte,
                updatedLte,
                0,
                20
        ))).thenReturn(expected);

        var response = fileSearchService.search(7L, new FileSearchQuery(
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

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<SearchFilesQuery> queryCaptor = ArgumentCaptor.forClass(SearchFilesQuery.class);
        verify(fileSearchApi).search(org.mockito.ArgumentMatchers.eq(7L), queryCaptor.capture());
        assertThat(queryCaptor.getValue().name()).isEqualTo(" note ");
        assertThat(queryCaptor.getValue().sizeGte()).isEqualTo(1L);
        assertThat(queryCaptor.getValue().updatedLte()).isEqualTo(updatedLte);
    }
}
