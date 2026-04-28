package com.yoyuzh.files.search.internal.application;

import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSearchQuery;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeFileSearchApiTest {

    @Mock
    private WorkspaceFileSearchApi workspaceFileSearchApi;

    private RuntimeFileSearchApi api;

    @BeforeEach
    void setUp() {
        api = new RuntimeFileSearchApi(workspaceFileSearchApi);
    }

    @Test
    void shouldTrimNameAndDelegateSearchToWorkspaceApi() {
        LocalDateTime createdGte = LocalDateTime.of(2026, 4, 1, 8, 0);
        LocalDateTime createdLte = LocalDateTime.of(2026, 4, 2, 8, 0);
        LocalDateTime updatedGte = LocalDateTime.of(2026, 4, 3, 8, 0);
        LocalDateTime updatedLte = LocalDateTime.of(2026, 4, 4, 8, 0);
        PageResponse<FileMetadataResponse> expected = new PageResponse<>(
                List.of(new FileMetadataResponse(
                        9L,
                        "report.pdf",
                        "/docs",
                        128L,
                        "application/pdf",
                        false,
                        createdGte,
                        createdGte,
                        null,
                        null,
                        false
                )),
                1,
                0,
                20
        );
        when(workspaceFileSearchApi.search(eq(7L), any(WorkspaceFileSearchQuery.class)))
                .thenReturn(expected);

        PageResponse<FileMetadataResponse> response = api.search(7L, new SearchFilesQuery(
                " report ",
                null,
                false,
                1L,
                256L,
                createdGte,
                createdLte,
                updatedGte,
                updatedLte,
                0,
                20
        ));

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<WorkspaceFileSearchQuery> queryCaptor = ArgumentCaptor.forClass(WorkspaceFileSearchQuery.class);
        verify(workspaceFileSearchApi).search(eq(7L), queryCaptor.capture());
        WorkspaceFileSearchQuery query = queryCaptor.getValue();
        assertThat(query.name()).isEqualTo("report");
        assertThat(query.category()).isNull();
        assertThat(query.directory()).isFalse();
        assertThat(query.sizeGte()).isEqualTo(1L);
        assertThat(query.sizeLte()).isEqualTo(256L);
        assertThat(query.createdGte()).isEqualTo(createdGte);
        assertThat(query.createdLte()).isEqualTo(createdLte);
        assertThat(query.updatedGte()).isEqualTo(updatedGte);
        assertThat(query.updatedLte()).isEqualTo(updatedLte);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void shouldNormalizeBlankNameToNullWhenDelegatingSearch() {
        PageResponse<FileMetadataResponse> expected = new PageResponse<>(List.of(), 0, 1, 100);
        when(workspaceFileSearchApi.search(eq(7L), any(WorkspaceFileSearchQuery.class)))
                .thenReturn(expected);

        PageResponse<FileMetadataResponse> response = api.search(7L, new SearchFilesQuery(
                "   ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                100
        ));

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<WorkspaceFileSearchQuery> queryCaptor = ArgumentCaptor.forClass(WorkspaceFileSearchQuery.class);
        verify(workspaceFileSearchApi).search(eq(7L), queryCaptor.capture());
        assertThat(queryCaptor.getValue().name()).isNull();
    }

    @Test
    void shouldDelegateCategoryToWorkspaceApi() {
        PageResponse<FileMetadataResponse> expected = new PageResponse<>(List.of(), 0, 0, 20);
        when(workspaceFileSearchApi.search(eq(7L), any(WorkspaceFileSearchQuery.class)))
                .thenReturn(expected);

        PageResponse<FileMetadataResponse> response = api.search(7L, new SearchFilesQuery(
                null,
                "image",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        ));

        assertThat(response).isSameAs(expected);
        ArgumentCaptor<WorkspaceFileSearchQuery> queryCaptor = ArgumentCaptor.forClass(WorkspaceFileSearchQuery.class);
        verify(workspaceFileSearchApi).search(eq(7L), queryCaptor.capture());
        assertThat(queryCaptor.getValue().category()).isEqualTo("image");
    }

    @Test
    void shouldRejectNegativePage() {
        assertInvalid(new SearchFilesQuery(null, null, null, null, null, null, null, null, null, -1, 20),
                "分页页码不能小于 0");
    }

    @Test
    void shouldRejectPageSizeBelowOne() {
        assertInvalid(new SearchFilesQuery(null, null, null, null, null, null, null, null, null, 0, 0),
                "分页大小必须在 1 到 100 之间");
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        assertInvalid(new SearchFilesQuery(null, null, null, null, null, null, null, null, null, 0, 101),
                "分页大小必须在 1 到 100 之间");
    }

    @Test
    void shouldRejectNegativeMinimumSize() {
        assertInvalid(new SearchFilesQuery(null, null, null, -1L, null, null, null, null, null, 0, 20),
                "文件大小下限不能小于 0");
    }

    @Test
    void shouldRejectNegativeMaximumSize() {
        assertInvalid(new SearchFilesQuery(null, null, null, null, -1L, null, null, null, null, 0, 20),
                "文件大小上限不能小于 0");
    }

    @Test
    void shouldRejectReversedSizeRange() {
        assertInvalid(new SearchFilesQuery(null, null, null, 20L, 10L, null, null, null, null, 0, 20),
                "文件大小范围不合法");
    }

    @Test
    void shouldRejectReversedCreatedRange() {
        LocalDateTime later = LocalDateTime.of(2026, 4, 2, 8, 0);
        LocalDateTime earlier = LocalDateTime.of(2026, 4, 1, 8, 0);

        assertInvalid(new SearchFilesQuery(null, null, null, null, null, later, earlier, null, null, 0, 20),
                "创建时间范围不合法");
    }

    @Test
    void shouldRejectReversedUpdatedRange() {
        LocalDateTime later = LocalDateTime.of(2026, 4, 2, 8, 0);
        LocalDateTime earlier = LocalDateTime.of(2026, 4, 1, 8, 0);

        assertInvalid(new SearchFilesQuery(null, null, null, null, null, null, null, later, earlier, 0, 20),
                "更新时间范围不合法");
    }

    private void assertInvalid(SearchFilesQuery query, String message) {
        assertThatThrownBy(() -> api.search(7L, query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message);
        verify(workspaceFileSearchApi, never()).search(any(), any());
    }
}
