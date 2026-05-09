package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.RemoteDownloadDetailResponse;
import com.yoyuzh.transfer.api.SelectRemoteDownloadFilesCommand;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadCandidateFile;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.Aria2Client;
import com.yoyuzh.transfer.internal.infra.QbittorrentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeRemoteDownloadApiTest {

    private RemoteDownloadService remoteDownloadService;
    private Aria2Client aria2Client;
    private QbittorrentClient qbittorrentClient;
    private RuntimeRemoteDownloadApi remoteDownloadApi;

    @BeforeEach
    void setUp() {
        remoteDownloadService = mock(RemoteDownloadService.class);
        aria2Client = mock(Aria2Client.class);
        qbittorrentClient = mock(QbittorrentClient.class);
        remoteDownloadApi = new RuntimeRemoteDownloadApi(remoteDownloadService, aria2Client, qbittorrentClient);
    }

    @Test
    void shouldPushQbFileSelectionBeforePersistingSelection() {
        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(
                7L,
                "/downloads",
                "magnet:?xt=urn:btih:demo",
                "local-default"
        );
        setTaskId(task, 21L);
        task.setDownloaderTaskId("hash-123");
        task.addCandidateFile(candidate("0"));
        task.addCandidateFile(candidate("1"));
        when(remoteDownloadService.loadOwnedTask(7L, 21L)).thenReturn(task);
        when(remoteDownloadService.selectFiles(eq(7L), eq(21L), eq(new SelectRemoteDownloadFilesCommand(List.of("0")))))
                .thenReturn(detailResponse("DOWNLOADING"));

        remoteDownloadApi.selectFiles(7L, 21L, new SelectRemoteDownloadFilesCommand(List.of("0")));

        verify(qbittorrentClient).updateFileSelection("hash-123", List.of("0"), List.of("1"));
        verify(remoteDownloadService).selectFiles(7L, 21L, new SelectRemoteDownloadFilesCommand(List.of("0")));
    }

    @Test
    void shouldCancelQbTaskWhenUserClearsAllSelections() {
        RemoteDownloadTask task = RemoteDownloadTask.createMagnet(
                7L,
                "/downloads",
                "magnet:?xt=urn:btih:demo",
                "local-default"
        );
        setTaskId(task, 22L);
        task.setDownloaderTaskId("hash-123");
        when(remoteDownloadService.loadOwnedTask(7L, 22L)).thenReturn(task);
        when(remoteDownloadService.cancel(7L, 22L)).thenReturn(detailResponse("CANCELED"));

        remoteDownloadApi.selectFiles(7L, 22L, new SelectRemoteDownloadFilesCommand(List.of()));

        verify(qbittorrentClient).delete("hash-123", true);
        verify(remoteDownloadService).cancel(7L, 22L);
    }

    @Test
    void shouldCancelAria2TaskBeforeMarkingRemoteDownloadCanceled() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 23L);
        task.setDownloaderTaskId("gid-123");
        when(remoteDownloadService.loadOwnedTask(7L, 23L)).thenReturn(task);
        when(remoteDownloadService.cancel(7L, 23L)).thenReturn(detailResponse("CANCELED"));

        remoteDownloadApi.cancel(7L, 23L);

        verify(aria2Client).cancel("gid-123");
        verify(remoteDownloadService).cancel(7L, 23L);
    }

    private RemoteDownloadCandidateFile candidate(String fileKey) {
        RemoteDownloadCandidateFile candidateFile = new RemoteDownloadCandidateFile();
        candidateFile.setFileKey(fileKey);
        candidateFile.setRelativePath(fileKey + ".bin");
        candidateFile.setSize(1L);
        return candidateFile;
    }

    private RemoteDownloadDetailResponse detailResponse(String status) {
        return new RemoteDownloadDetailResponse(
                1L,
                2L,
                status,
                "MAGNET",
                "QBITTORRENT",
                "/downloads",
                "source",
                "source",
                "local-default",
                0,
                0,
                null,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                null
        );
    }

    private void setTaskId(RemoteDownloadTask task, long id) {
        ReflectionTestUtils.setField(task, "id", id);
    }
}
