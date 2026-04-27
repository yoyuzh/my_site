package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.CreateRemoteDownloadCommand;
import com.yoyuzh.transfer.api.RemoteDownloadDetailResponse;
import com.yoyuzh.transfer.internal.domain.DownloadEngineType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadSourceType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.RemoteDownloadTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteDownloadServiceTest {

    private RemoteDownloadTaskRepository remoteDownloadTaskRepository;
    private BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private RemoteDownloadService remoteDownloadService;

    @BeforeEach
    void setUp() {
        remoteDownloadTaskRepository = mock(RemoteDownloadTaskRepository.class);
        backgroundTaskLifecycleApi = mock(BackgroundTaskLifecycleApi.class);
        remoteDownloadService = new RemoteDownloadService(remoteDownloadTaskRepository, backgroundTaskLifecycleApi);
    }

    @Test
    void shouldCreateHttpTaskWithAria2EngineAndPendingStatus() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );

        assertThat(task.getUserId()).isEqualTo(7L);
        assertThat(task.getTargetPath()).isEqualTo("/downloads");
        assertThat(task.getEngineType()).isEqualTo(DownloadEngineType.ARIA2);
        assertThat(task.getStatus()).isEqualTo(RemoteDownloadStatus.PENDING);
    }

    @Test
    void shouldCreateQueuedBackgroundTaskWhenCreatingRemoteDownload() {
        when(remoteDownloadTaskRepository.save(any(RemoteDownloadTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(backgroundTaskLifecycleApi.createQueuedTaskByUserId(eq(7L), eq(BackgroundTaskType.REMOTE_DOWNLOAD), any(), any(), any()))
                .thenReturn(new BackgroundTaskView(
                        91L,
                        BackgroundTaskType.REMOTE_DOWNLOAD,
                        BackgroundTaskStatus.QUEUED,
                        7L,
                        "{}",
                        "remote-download:7:1",
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null
                ));

        RemoteDownloadDetailResponse response = remoteDownloadService.create(7L, new CreateRemoteDownloadCommand(
                RemoteDownloadSourceType.HTTP,
                "https://example.com/demo.zip",
                null,
                null,
                "/downloads"
        ));

        assertThat(response.status()).isEqualTo(RemoteDownloadStatus.PENDING.name());
        assertThat(response.engineType()).isEqualTo(DownloadEngineType.ARIA2.name());
        assertThat(response.backgroundTaskId()).isEqualTo(91L);
        assertThat(response.targetPath()).isEqualTo("/downloads");
    }

    @Test
    void shouldRejectMarkCompletedFromPendingState() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 11L);
        when(remoteDownloadTaskRepository.findById(11L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> remoteDownloadService.markCompleted(11L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void shouldRejectCancelAfterCompletion() {
        RemoteDownloadTask task = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(task, 12L);
        task.setStatus(RemoteDownloadStatus.COMPLETED);
        when(remoteDownloadTaskRepository.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> remoteDownloadService.cancel(7L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void shouldRejectMissingHttpSourceWithEnglishValidationMessage() {
        assertThatThrownBy(() -> remoteDownloadService.create(7L, new CreateRemoteDownloadCommand(
                RemoteDownloadSourceType.HTTP,
                "   ",
                null,
                null,
                "/downloads"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http source is required");
    }

    @Test
    void shouldRejectMissingTorrentPayloadWithEnglishValidationMessage() {
        assertThatThrownBy(() -> remoteDownloadService.create(7L, new CreateRemoteDownloadCommand(
                RemoteDownloadSourceType.TORRENT_FILE,
                null,
                "demo.torrent",
                null,
                "/downloads"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("torrent file content is required");
    }

    private void setTaskId(RemoteDownloadTask task, long id) {
        ReflectionTestUtils.setField(task, "id", id);
    }
}
