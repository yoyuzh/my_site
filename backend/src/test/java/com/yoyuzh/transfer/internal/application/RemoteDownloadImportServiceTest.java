package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceExternalFileImport;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteDownloadImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldImportCompletedAria2FileIntoWorkspaceAndDeleteLocalFile() throws Exception {
        IdentityUserDirectoryApi identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        WorkspaceBootstrapApi workspaceBootstrapApi = mock(WorkspaceBootstrapApi.class);
        RemoteDownloadImportService importService = new RemoteDownloadImportService(identityUserDirectoryApi, workspaceBootstrapApi);
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(7L)));
        when(workspaceBootstrapApi.existsNode(eq(new WorkspaceUserContext(7L, 1024L, 1024L)), eq("/docs"), eq("demo.txt")))
                .thenReturn(false);

        Path downloadedFile = tempDir.resolve("demo.txt");
        Files.writeString(downloadedFile, "hello");

        RemoteDownloadTask task = RemoteDownloadTask.createHttp(7L, "/docs", "https://example.com/demo.txt", "local-default");
        setTaskId(task, 11L);

        int importedCount = importService.importCompletedDownload(task, downloadedFile.toString(), null);

        assertThat(importedCount).isEqualTo(1);
        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                eq(new WorkspaceUserContext(7L, 1024L, 1024L)),
                eq(List.of()),
                argThat(files -> files.size() == 1
                        && "/docs".equals(files.get(0).path())
                        && "demo.txt".equals(files.get(0).filename())
                        && "text/plain".equals(files.get(0).contentType())
                        && files.get(0).size() == 5L),
                eq(null)
        );
        assertThat(Files.exists(downloadedFile)).isFalse();
    }

    @Test
    void shouldResolveDuplicateAria2FilenameBeforeImporting() throws Exception {
        IdentityUserDirectoryApi identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        WorkspaceBootstrapApi workspaceBootstrapApi = mock(WorkspaceBootstrapApi.class);
        RemoteDownloadImportService importService = new RemoteDownloadImportService(identityUserDirectoryApi, workspaceBootstrapApi);
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(7L)));
        when(workspaceBootstrapApi.existsNode(eq(new WorkspaceUserContext(7L, 1024L, 1024L)), eq("/docs"), eq("demo.txt")))
                .thenReturn(true);
        when(workspaceBootstrapApi.existsNode(eq(new WorkspaceUserContext(7L, 1024L, 1024L)), eq("/docs"), eq("demo(1).txt")))
                .thenReturn(true);
        when(workspaceBootstrapApi.existsNode(eq(new WorkspaceUserContext(7L, 1024L, 1024L)), eq("/docs"), eq("demo(2).txt")))
                .thenReturn(false);

        Path downloadedFile = tempDir.resolve("demo.txt");
        Files.writeString(downloadedFile, "hello");

        RemoteDownloadTask task = RemoteDownloadTask.createHttp(7L, "/docs", "https://example.com/demo.txt", "local-default");
        setTaskId(task, 13L);

        int importedCount = importService.importCompletedDownload(task, downloadedFile.toString(), null);

        assertThat(importedCount).isEqualTo(1);
        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                eq(new WorkspaceUserContext(7L, 1024L, 1024L)),
                eq(List.of()),
                argThat(files -> files.size() == 1 && "demo(2).txt".equals(files.get(0).filename())),
                eq(null)
        );
    }

    @Test
    void shouldImportSelectedQbittorrentFilesRelativeToTargetPath() throws Exception {
        IdentityUserDirectoryApi identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        WorkspaceBootstrapApi workspaceBootstrapApi = mock(WorkspaceBootstrapApi.class);
        RemoteDownloadImportService importService = new RemoteDownloadImportService(identityUserDirectoryApi, workspaceBootstrapApi);
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(7L)));

        Path savePath = tempDir.resolve("downloads");
        Files.createDirectories(savePath.resolve("album/disc1"));
        Files.writeString(savePath.resolve("album/disc1/track01.mp3"), "audio-1");
        Files.writeString(savePath.resolve("cover.jpg"), "image-1");

        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(7L, "/music", "magnet:?xt=urn:btih:demo", "local-default");
        setTaskId(task, 12L);
        task.addCandidateFile(selectedCandidate("0", "album/disc1/track01.mp3"));
        task.addCandidateFile(selectedCandidate("1", "cover.jpg"));
        task.setSelectedFileCount(2);

        int importedCount = importService.importCompletedDownload(task, savePath.resolve("album").toString(), savePath.toString());

        assertThat(importedCount).isEqualTo(2);
        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                eq(new WorkspaceUserContext(7L, 1024L, 1024L)),
                argThat(directories -> directories.contains("/music/album") && directories.contains("/music/album/disc1")),
                argThat(files -> files.size() == 2
                        && containsFile(files, "/music/album/disc1", "track01.mp3", "audio-1")
                        && containsFile(files, "/music", "cover.jpg", "image-1")),
                eq(null)
        );
    }

    @Test
    void shouldResolveDuplicateQbittorrentFilenamesBeforeImporting() throws Exception {
        IdentityUserDirectoryApi identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        WorkspaceBootstrapApi workspaceBootstrapApi = mock(WorkspaceBootstrapApi.class);
        RemoteDownloadImportService importService = new RemoteDownloadImportService(identityUserDirectoryApi, workspaceBootstrapApi);
        WorkspaceUserContext workspaceUser = new WorkspaceUserContext(7L, 1024L, 1024L);
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(7L)));
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music/disc1"), eq("cover.jpg")))
                .thenReturn(true);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music/disc1"), eq("cover(1).jpg")))
                .thenReturn(false);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music/disc2"), eq("cover.jpg")))
                .thenReturn(false);

        Path savePath = tempDir.resolve("downloads");
        Files.createDirectories(savePath.resolve("disc1"));
        Files.createDirectories(savePath.resolve("disc2"));
        Files.writeString(savePath.resolve("disc1/cover.jpg"), "image-1");
        Files.writeString(savePath.resolve("disc2/cover.jpg"), "image-2");

        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(7L, "/music", "magnet:?xt=urn:btih:demo", "local-default");
        setTaskId(task, 14L);
        task.addCandidateFile(selectedCandidate("0", "disc1/cover.jpg"));
        task.addCandidateFile(selectedCandidate("1", "disc2/cover.jpg"));
        task.setSelectedFileCount(2);

        int importedCount = importService.importCompletedDownload(task, savePath.toString(), savePath.toString());

        assertThat(importedCount).isEqualTo(2);
        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                eq(workspaceUser),
                argThat(directories -> directories.contains("/music/disc1") && directories.contains("/music/disc2")),
                argThat(files -> files.size() == 2
                        && containsFile(files, "/music/disc1", "cover(1).jpg", "image-1")
                        && containsFile(files, "/music/disc2", "cover.jpg", "image-2")),
                eq(null)
        );
    }

    @Test
    void shouldResolveDuplicateQbittorrentDirectoryBeforeImporting() throws Exception {
        IdentityUserDirectoryApi identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        WorkspaceBootstrapApi workspaceBootstrapApi = mock(WorkspaceBootstrapApi.class);
        RemoteDownloadImportService importService = new RemoteDownloadImportService(identityUserDirectoryApi, workspaceBootstrapApi);
        WorkspaceUserContext workspaceUser = new WorkspaceUserContext(7L, 1024L, 1024L);
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(7L)));
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music"), eq("album")))
                .thenReturn(true);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music"), eq("album(1)")))
                .thenReturn(false);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music/album(1)"), eq("disc1")))
                .thenReturn(false);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music/album(1)/disc1"), eq("track01.mp3")))
                .thenReturn(false);

        Path savePath = tempDir.resolve("downloads");
        Files.createDirectories(savePath.resolve("album/disc1"));
        Files.writeString(savePath.resolve("album/disc1/track01.mp3"), "audio-1");

        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(7L, "/music", "magnet:?xt=urn:btih:demo", "local-default");
        setTaskId(task, 16L);
        task.addCandidateFile(selectedCandidate("0", "album/disc1/track01.mp3"));
        task.setSelectedFileCount(1);

        int importedCount = importService.importCompletedDownload(task, savePath.toString(), savePath.toString());

        assertThat(importedCount).isEqualTo(1);
        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                eq(workspaceUser),
                argThat(directories -> directories.contains("/music/album(1)") && directories.contains("/music/album(1)/disc1")),
                argThat(files -> files.size() == 1
                        && containsFile(files, "/music/album(1)/disc1", "track01.mp3", "audio-1")),
                eq(null)
        );
    }

    @Test
    void shouldResolveDuplicateQbittorrentFilenamesWithinSameTargetDirectory() throws Exception {
        IdentityUserDirectoryApi identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        WorkspaceBootstrapApi workspaceBootstrapApi = mock(WorkspaceBootstrapApi.class);
        RemoteDownloadImportService importService = new RemoteDownloadImportService(identityUserDirectoryApi, workspaceBootstrapApi);
        WorkspaceUserContext workspaceUser = new WorkspaceUserContext(7L, 1024L, 1024L);
        when(identityUserDirectoryApi.findSnapshotById(7L)).thenReturn(java.util.Optional.of(snapshot(7L)));
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music"), eq("cover.jpg")))
                .thenReturn(true);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music"), eq("cover(1).jpg")))
                .thenReturn(false);
        when(workspaceBootstrapApi.existsNode(eq(workspaceUser), eq("/music"), eq("cover(2).jpg")))
                .thenReturn(false);

        Path savePath = tempDir.resolve("downloads");
        Files.createDirectories(savePath);
        Files.writeString(savePath.resolve("cover.jpg"), "image-1");
        Files.writeString(savePath.resolve("cover(1).jpg"), "image-2");

        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(7L, "/music", "magnet:?xt=urn:btih:demo", "local-default");
        setTaskId(task, 15L);
        task.addCandidateFile(selectedCandidate("0", "cover.jpg"));
        task.addCandidateFile(selectedCandidate("1", "cover(1).jpg"));
        task.setSelectedFileCount(2);

        int importedCount = importService.importCompletedDownload(task, savePath.toString(), savePath.toString());

        assertThat(importedCount).isEqualTo(2);
        verify(workspaceBootstrapApi).importExternalFilesAtomically(
                eq(workspaceUser),
                eq(List.of()),
                argThat(files -> files.size() == 2
                        && containsFile(files, "/music", "cover(1).jpg", "image-1")
                        && containsFile(files, "/music", "cover(1)(1).jpg", "image-2")),
                eq(null)
        );
    }

    private boolean containsFile(List<WorkspaceExternalFileImport> files, String path, String filename, String content) {
        return files.stream().anyMatch(file ->
                path.equals(file.path())
                        && filename.equals(file.filename())
                        && content.equals(new String(file.content())));
    }

    private RemoteDownloadCandidateFile selectedCandidate(String fileKey, String relativePath) {
        RemoteDownloadCandidateFile candidateFile = new RemoteDownloadCandidateFile();
        candidateFile.setFileKey(fileKey);
        candidateFile.setRelativePath(relativePath);
        candidateFile.setSize(1L);
        candidateFile.setSelected(true);
        return candidateFile;
    }

    private IdentityUserSnapshot snapshot(Long userId) {
        return new IdentityUserSnapshot(
                userId,
                "user-" + userId,
                "user-" + userId,
                "user-" + userId + "@example.com",
                null,
                null,
                "zh-CN",
                null,
                null,
                null,
                IdentityRoleName.USER,
                LocalDateTime.now(),
                1024L,
                1024L
        );
    }

    private void setTaskId(RemoteDownloadTask task, long id) {
        ReflectionTestUtils.setField(task, "id", id);
    }
}
