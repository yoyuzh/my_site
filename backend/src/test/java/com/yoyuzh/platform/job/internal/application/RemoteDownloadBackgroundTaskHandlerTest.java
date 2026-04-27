package com.yoyuzh.platform.job.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.transfer.api.RemoteDownloadExecutionApi;
import com.yoyuzh.transfer.api.RemoteDownloadExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteDownloadBackgroundTaskHandlerTest {

    @Mock
    private RemoteDownloadExecutionApi remoteDownloadExecutionApi;

    private RemoteDownloadBackgroundTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemoteDownloadBackgroundTaskHandler(
                remoteDownloadExecutionApi,
                new BackgroundTaskStateManager(new ObjectMapper())
        );
    }

    @Test
    void shouldSubmitHttpTaskToAria2AndReportDownloadingPhase() {
        when(remoteDownloadExecutionApi.start(11L)).thenReturn(new RemoteDownloadExecutionResult(
                11L,
                "ARIA2",
                "downloading",
                "gid-123",
                false,
                15L
        ));

        List<Map<String, Object>> progressPatches = new ArrayList<>();
        BackgroundTaskHandlerResult result = handler.handle(backgroundTask(11L), progressPatches::add);

        assertThat(result.publicStatePatch()).containsEntry("phase", "downloading");
        assertThat(result.publicStatePatch()).containsEntry("engineType", "ARIA2");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        assertThat(progressPatches).hasSize(1);
        assertThat(progressPatches.get(0)).containsEntry("phase", "downloading");
        verify(remoteDownloadExecutionApi).start(11L);
    }

    @Test
    void shouldSubmitMagnetTaskToQbittorrentAndReportMetadataPhase() {
        when(remoteDownloadExecutionApi.start(12L)).thenReturn(new RemoteDownloadExecutionResult(
                12L,
                "QBITTORRENT",
                "fetching-metadata",
                "hash-123",
                false,
                15L
        ));

        List<Map<String, Object>> progressPatches = new ArrayList<>();
        BackgroundTaskHandlerResult result = handler.handle(backgroundTask(12L), progressPatches::add);

        assertThat(result.publicStatePatch()).containsEntry("phase", "fetching-metadata");
        assertThat(result.publicStatePatch()).containsEntry("engineType", "QBITTORRENT");
        assertThat(result.completed()).isFalse();
        assertThat(result.nextRunDelaySeconds()).isEqualTo(15L);
        assertThat(progressPatches).hasSize(1);
        assertThat(progressPatches.get(0)).containsEntry("phase", "fetching-metadata");
        verify(remoteDownloadExecutionApi).start(12L);
    }

    private BackgroundTask backgroundTask(Long remoteDownloadId) {
        BackgroundTask task = new BackgroundTask();
        task.setId(remoteDownloadId);
        task.setType(BackgroundTaskType.REMOTE_DOWNLOAD);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(7L);
        task.setPrivateStateJson("{\"remoteDownloadId\":" + remoteDownloadId + "}");
        task.setPublicStateJson("{}");
        return task;
    }
}
