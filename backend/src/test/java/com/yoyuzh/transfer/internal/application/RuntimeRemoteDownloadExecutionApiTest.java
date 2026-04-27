package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.RemoteDownloadExecutionResult;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.Aria2Client;
import com.yoyuzh.transfer.internal.infra.QbittorrentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeRemoteDownloadExecutionApiTest {

    private RemoteDownloadService remoteDownloadService;
    private Aria2Client aria2Client;
    private QbittorrentClient qbittorrentClient;
    private RemoteDownloadImportService remoteDownloadImportService;
    private RuntimeRemoteDownloadExecutionApi executionApi;

    @BeforeEach
    void setUp() {
        remoteDownloadService = mock(RemoteDownloadService.class);
        aria2Client = mock(Aria2Client.class);
        qbittorrentClient = mock(QbittorrentClient.class);
        remoteDownloadImportService = mock(RemoteDownloadImportService.class);
        executionApi = new RuntimeRemoteDownloadExecutionApi(
                remoteDownloadService,
                aria2Client,
                qbittorrentClient,
                remoteDownloadImportService
        );
    }

    @Test
    void shouldRouteHttpTaskToAria2() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 11L);
        when(remoteDownloadService.loadForExecution(11L)).thenReturn(task);
        when(aria2Client.submitHttp("https://example.com/demo.zip", "local-default")).thenReturn("gid-123");

        RemoteDownloadExecutionResult result = executionApi.start(11L);

        assertThat(result.engineType()).isEqualTo("ARIA2");
        assertThat(result.phase()).isEqualTo("downloading");
        assertThat(result.downloaderTaskId()).isEqualTo("gid-123");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        verify(remoteDownloadService).markSubmitted(11L, "gid-123", RemoteDownloadStatus.DOWNLOADING);
    }

    @Test
    void shouldRouteMagnetTaskToQbittorrent() {
        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(
                7L,
                "/downloads",
                "magnet:?xt=urn:btih:demo",
                "local-default"
        );
        setTaskId(task, 12L);
        when(remoteDownloadService.loadForExecution(12L)).thenReturn(task);
        when(qbittorrentClient.submitMagnet("magnet:?xt=urn:btih:demo", "local-default")).thenReturn("hash-123");

        RemoteDownloadExecutionResult result = executionApi.start(12L);

        assertThat(result.engineType()).isEqualTo("QBITTORRENT");
        assertThat(result.phase()).isEqualTo("fetching-metadata");
        assertThat(result.downloaderTaskId()).isEqualTo("hash-123");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        verify(remoteDownloadService).markSubmitted(12L, "hash-123", RemoteDownloadStatus.FETCHING_METADATA);
    }

    @Test
    void shouldRouteTorrentTaskToQbittorrentUsingTorrentBinaryContent() {
        byte[] torrentContent = "demo-torrent".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RemoteDownloadTask task = RemoteDownloadTask.createTorrent(
                7L,
                "/downloads",
                "demo.torrent",
                torrentContent,
                "local-default"
        );
        setTaskId(task, 13L);
        when(remoteDownloadService.loadForExecution(13L)).thenReturn(task);
        when(qbittorrentClient.submitTorrent("demo.torrent", torrentContent, "local-default")).thenReturn("hash-456");

        RemoteDownloadExecutionResult result = executionApi.start(13L);

        assertThat(result.engineType()).isEqualTo("QBITTORRENT");
        assertThat(result.phase()).isEqualTo("fetching-metadata");
        assertThat(result.downloaderTaskId()).isEqualTo("hash-456");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        verify(qbittorrentClient).submitTorrent("demo.torrent", torrentContent, "local-default");
        verify(remoteDownloadService).markSubmitted(13L, "hash-456", RemoteDownloadStatus.FETCHING_METADATA);
    }

    @Test
    void shouldNotResubmitTaskWhenDownloaderTaskAlreadyExists() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 14L);
        task.setDownloaderTaskId("gid-existing");
        task.setStatus(RemoteDownloadStatus.DOWNLOADING);
        when(remoteDownloadService.loadForExecution(14L)).thenReturn(task);
        when(aria2Client.queryStatus("gid-existing")).thenReturn(new Aria2Client.TaskStatus(
                "gid-existing",
                "active",
                200L,
                50L,
                "/downloads/demo.zip",
                null,
                null
        ));

        RemoteDownloadExecutionResult result = executionApi.start(14L);

        assertThat(result.phase()).isEqualTo("downloading");
        assertThat(result.downloaderTaskId()).isEqualTo("gid-existing");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        verify(aria2Client, never()).submitHttp("https://example.com/demo.zip", "local-default");
        verify(remoteDownloadService, never()).markSubmitted(14L, "gid-existing", RemoteDownloadStatus.DOWNLOADING);
    }

    @Test
    void shouldAdvanceAria2TaskToCompletedWhenDownloaderFinished() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 15L);
        task.setDownloaderTaskId("gid-existing");
        task.setStatus(RemoteDownloadStatus.DOWNLOADING);
        when(remoteDownloadService.loadForExecution(15L)).thenReturn(task);
        when(aria2Client.queryStatus("gid-existing")).thenReturn(new Aria2Client.TaskStatus(
                "gid-existing",
                "complete",
                200L,
                200L,
                "/downloads/demo.zip",
                null,
                null
        ));
        when(remoteDownloadImportService.importCompletedDownload(task, "/downloads/demo.zip", null)).thenReturn(1);

        RemoteDownloadExecutionResult result = executionApi.start(15L);

        assertThat(result.phase()).isEqualTo("completed");
        assertThat(result.completed()).isTrue();
        assertThat(result.nextRunDelaySeconds()).isNull();
        verify(remoteDownloadService).markImporting(15L);
        verify(remoteDownloadImportService).importCompletedDownload(task, "/downloads/demo.zip", null);
        verify(remoteDownloadService).markCompleted(15L, 1);
    }

    @Test
    void shouldExposeBtCandidateFilesAndWaitForSelectionWhenMetadataIsReady() {
        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(
                7L,
                "/downloads",
                "magnet:?xt=urn:btih:demo",
                "local-default"
        );
        setTaskId(task, 16L);
        task.setDownloaderTaskId("hash-123");
        task.setStatus(RemoteDownloadStatus.FETCHING_METADATA);
        when(remoteDownloadService.loadForExecution(16L)).thenReturn(task);
        when(qbittorrentClient.queryTorrent("hash-123")).thenReturn(new QbittorrentClient.TorrentStatus(
                "hash-123",
                "downloading",
                0.0d,
                "/downloads/demo",
                "/downloads"
        ));
        when(qbittorrentClient.listFiles("hash-123")).thenReturn(java.util.List.of(
                new QbittorrentClient.TorrentFile("0", "movie.mkv", 1024L, 1),
                new QbittorrentClient.TorrentFile("1", "subtitle.srt", 32L, 0)
        ));

        RemoteDownloadExecutionResult result = executionApi.start(16L);

        assertThat(result.phase()).isEqualTo("awaiting-file-selection");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        verify(remoteDownloadService).markAwaitingFileSelection(
                org.mockito.ArgumentMatchers.eq(16L),
                argThat(files -> files.size() == 2
                        && "0".equals(files.get(0).getFileKey())
                        && "movie.mkv".equals(files.get(0).getRelativePath())
                        && files.get(0).getSize() == 1024L
                        && "1".equals(files.get(1).getFileKey())
                        && "subtitle.srt".equals(files.get(1).getRelativePath())
                        && files.get(1).getSize() == 32L)
        );
    }

    @Test
    void shouldStayInAwaitingFileSelectionBeforeUserChoosesFiles() {
        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(
                7L,
                "/downloads",
                "magnet:?xt=urn:btih:demo",
                "local-default"
        );
        setTaskId(task, 17L);
        task.setDownloaderTaskId("hash-123");
        task.setStatus(RemoteDownloadStatus.AWAITING_FILE_SELECTION);
        task.addCandidateFile(candidate("0", "movie.mkv", 1024L));
        when(remoteDownloadService.loadForExecution(17L)).thenReturn(task);
        when(qbittorrentClient.queryTorrent("hash-123")).thenReturn(new QbittorrentClient.TorrentStatus(
                "hash-123",
                "pausedDL",
                0.0d,
                "/downloads/demo",
                "/downloads"
        ));

        RemoteDownloadExecutionResult result = executionApi.start(17L);

        assertThat(result.phase()).isEqualTo("awaiting-file-selection");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        verify(remoteDownloadService, never()).markSubmitted(17L, "hash-123", RemoteDownloadStatus.DOWNLOADING);
    }

    @Test
    void shouldImportSelectedQbittorrentFilesBeforeCompletingTask() {
        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(
                7L,
                "/downloads",
                "magnet:?xt=urn:btih:demo",
                "local-default"
        );
        setTaskId(task, 18L);
        task.setDownloaderTaskId("hash-123");
        task.setStatus(RemoteDownloadStatus.DOWNLOADING);
        task.addCandidateFile(selectedCandidate("0", "movie.mkv", 1024L));
        task.addCandidateFile(selectedCandidate("1", "subtitle.srt", 32L));
        task.setSelectedFileCount(2);
        when(remoteDownloadService.loadForExecution(18L)).thenReturn(task);
        when(qbittorrentClient.queryTorrent("hash-123")).thenReturn(new QbittorrentClient.TorrentStatus(
                "hash-123",
                "uploading",
                1.0d,
                "/downloads/demo",
                "/downloads"
        ));
        when(remoteDownloadImportService.importCompletedDownload(task, "/downloads/demo", "/downloads")).thenReturn(2);

        RemoteDownloadExecutionResult result = executionApi.start(18L);

        assertThat(result.phase()).isEqualTo("completed");
        assertThat(result.completed()).isTrue();
        verify(remoteDownloadService).markImporting(18L);
        verify(remoteDownloadImportService).importCompletedDownload(task, "/downloads/demo", "/downloads");
        verify(qbittorrentClient).delete("hash-123", true);
        verify(remoteDownloadService).markCompleted(18L, 2);
    }

    @Test
    void shouldMarkTaskFailedWhenImportFails() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 19L);
        task.setDownloaderTaskId("gid-existing");
        task.setStatus(RemoteDownloadStatus.DOWNLOADING);
        when(remoteDownloadService.loadForExecution(19L)).thenReturn(task);
        when(aria2Client.queryStatus("gid-existing")).thenReturn(new Aria2Client.TaskStatus(
                "gid-existing",
                "complete",
                200L,
                200L,
                "/downloads/demo.zip",
                null,
                null
        ));
        when(remoteDownloadImportService.importCompletedDownload(task, "/downloads/demo.zip", null))
                .thenThrow(new IllegalStateException("import failed"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> executionApi.start(19L));

        verify(remoteDownloadService).markFailed(19L, "remote-download-import-failed", "import failed");
    }

    private RemoteDownloadCandidateFile candidate(String fileKey, String relativePath, long size) {
        RemoteDownloadCandidateFile candidateFile = new RemoteDownloadCandidateFile();
        candidateFile.setFileKey(fileKey);
        candidateFile.setRelativePath(relativePath);
        candidateFile.setSize(size);
        return candidateFile;
    }

    private RemoteDownloadCandidateFile selectedCandidate(String fileKey, String relativePath, long size) {
        RemoteDownloadCandidateFile candidateFile = candidate(fileKey, relativePath, size);
        candidateFile.setSelected(true);
        return candidateFile;
    }

    private void setTaskId(RemoteDownloadTask task, long id) {
        ReflectionTestUtils.setField(task, "id", id);
    }
}
