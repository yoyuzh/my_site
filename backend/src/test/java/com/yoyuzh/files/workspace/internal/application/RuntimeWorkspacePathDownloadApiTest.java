package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadStreamResult;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeWorkspacePathDownloadApiTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private FileService fileService;

    @Test
    void shouldDownloadOwnedFileByLogicalPath() {
        RuntimeWorkspacePathDownloadApi api = createApi();
        StoredFile file = createFile(11L, 7L, "/", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "a.txt"))
                .thenReturn(Optional.of(file));
        when(fileService.download(7L, 11L))
                .thenReturn(WorkspaceDownloadResult.inline("a.txt", "text/plain", "hello".getBytes(UTF_8)));

        WorkspaceDownloadResult result = api.downloadOwnedFileByPath(7L, "/a.txt");

        assertThat(result.redirect()).isFalse();
        assertThat(result.body()).isEqualTo("hello".getBytes(UTF_8));
        assertThat(result.filename()).isEqualTo("a.txt");
        verify(fileService).download(7L, 11L);
    }

    @Test
    void shouldStreamOwnedFileByLogicalPathForWebDav() {
        RuntimeWorkspacePathDownloadApi api = createApi();
        StoredFile file = createFile(11L, 7L, "/", "a.txt", false);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "a.txt"))
                .thenReturn(Optional.of(file));
        when(fileService.downloadStream(7L, 11L))
                .thenReturn(new WorkspaceDownloadStreamResult(
                        "a.txt",
                        "text/plain",
                        new java.io.ByteArrayInputStream("hello".getBytes(UTF_8)),
                        5L
                ));

        WorkspaceDownloadStreamResult result = api.streamOwnedFileByPath(7L, "/a.txt");

        assertThat(result.filename()).isEqualTo("a.txt");
        assertThat(result.contentLength()).isEqualTo(5L);
        verify(fileService).downloadStream(7L, 11L);
    }

    @Test
    void shouldRejectDirectoryDownloadByPath() {
        RuntimeWorkspacePathDownloadApi api = createApi();
        StoredFile directory = createFile(12L, 7L, "/", "Docs", true);
        when(storedFileRepository.findByUserIdAndPathAndFilename(7L, "/", "Docs"))
                .thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> api.downloadOwnedFileByPath(7L, "/Docs"))
                .isInstanceOf(BusinessException.class);
    }

    private RuntimeWorkspacePathDownloadApi createApi() {
        return new RuntimeWorkspacePathDownloadApi(
                new RuntimeWorkspacePathNodeApi(
                        storedFileRepository,
                        new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage),
                        new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage)
                ),
                fileService
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
