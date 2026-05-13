package com.yoyuzh.files.webdav.internal.application;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathDownloadApi;
import com.yoyuzh.files.workspace.api.WorkspacePathNodeApi;
import com.yoyuzh.files.workspace.api.WorkspacePathWriteApi;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceWebDavResourceStoreTest {

    @Mock
    private WorkspacePathNodeApi workspacePathNodeApi;

    @Mock
    private WorkspacePathDownloadApi workspacePathDownloadApi;

    @Mock
    private WorkspacePathWriteApi workspacePathWriteApi;

    @Test
    void shouldListAllDirectoryPages() {
        WorkspaceWebDavResourceStore store = new WorkspaceWebDavResourceStore(
                workspacePathNodeApi,
                workspacePathDownloadApi,
                workspacePathWriteApi
        );
        WebDavPrincipal principal = new WebDavPrincipal(7L, "alice", 1024L, 512L);
        List<FileMetadataResponse> firstPage = IntStream.range(0, 1000)
                .mapToObj(index -> file(10L + index, "/Docs", "file-%04d.txt".formatted(index)))
                .toList();
        when(workspacePathNodeApi.listOwnedDirectory(7L, "/Docs", 0, 1000))
                .thenReturn(new PageResponse<>(firstPage, 1001L, 0, 1000));
        when(workspacePathNodeApi.listOwnedDirectory(7L, "/Docs", 1, 1000))
                .thenReturn(new PageResponse<>(List.of(file(1010L, "/Docs", "last.txt")), 1001L, 1, 1000));

        List<WebDavStoredResource> resources = store.list(principal, "/Docs");

        assertThat(resources)
                .extracting(WebDavStoredResource::path)
                .hasSize(1001)
                .contains("/Docs/file-0000.txt", "/Docs/last.txt");
    }

    private FileMetadataResponse file(Long id, String path, String filename) {
        LocalDateTime now = LocalDateTime.now();
        return new FileMetadataResponse(
                id,
                filename,
                path,
                5L,
                "text/plain",
                false,
                now,
                now,
                null,
                null,
                false
        );
    }
}
