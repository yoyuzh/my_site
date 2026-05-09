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
import com.yoyuzh.transfer.api.RemoteDownloadSourceType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadTask;
import com.yoyuzh.transfer.internal.infra.RemoteDownloadTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void shouldCreateNewTaskWhenRetryingRemoteDownload() {
        RemoteDownloadTask failedTask = RemoteDownloadTask.createHttp(
                7L,
                "/downloads",
                "https://example.com/demo.zip",
                "local-default"
        );
        setTaskId(failedTask, 31L);
        failedTask.setStatus(RemoteDownloadStatus.FAILED);
        failedTask.setFailureCode("2");
        failedTask.setFailureMessage("Timeout.");
        failedTask.setFinishedAt(Instant.parse("2026-05-09T08:00:00Z"));

        when(remoteDownloadTaskRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(failedTask));
        when(remoteDownloadTaskRepository.save(any(RemoteDownloadTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(backgroundTaskLifecycleApi.createQueuedTaskByUserId(eq(7L), eq(BackgroundTaskType.REMOTE_DOWNLOAD), any(), any(), any()))
                .thenReturn(new BackgroundTaskView(
                        99L,
                        BackgroundTaskType.REMOTE_DOWNLOAD,
                        BackgroundTaskStatus.QUEUED,
                        7L,
                        "{}",
                        "remote-download:7:retry",
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null
                ));

        RemoteDownloadDetailResponse response = remoteDownloadService.retry(7L, 31L);

        assertThat(response.status()).isEqualTo(RemoteDownloadStatus.PENDING.name());
        assertThat(response.backgroundTaskId()).isEqualTo(99L);
        assertThat(response.sourceValue()).isEqualTo("https://example.com/demo.zip");
        assertThat(response.targetPath()).isEqualTo("/downloads");
        ArgumentCaptor<RemoteDownloadTask> savedTasks = ArgumentCaptor.forClass(RemoteDownloadTask.class);
        verify(remoteDownloadTaskRepository, org.mockito.Mockito.times(2)).save(savedTasks.capture());
        RemoteDownloadTask finalSavedTask = savedTasks.getAllValues().get(1);
        assertThat(finalSavedTask).isNotSameAs(failedTask);
        assertThat(finalSavedTask.getStatus()).isEqualTo(RemoteDownloadStatus.PENDING);
        assertThat(finalSavedTask.getFailureCode()).isNull();
        assertThat(finalSavedTask.getFailureMessage()).isNull();
        assertThat(finalSavedTask.getFinishedAt()).isNull();
        assertThat(finalSavedTask.getBackgroundTaskId()).isEqualTo(99L);
    }

    @Test
    void shouldListOnlyRecentRemoteDownloads() {
        RemoteDownloadTask recent = RemoteDownloadTask.createHttp(7L, "/downloads", "https://example.com/recent.zip", "local-default");
        RemoteDownloadTask oldCompleted = RemoteDownloadTask.createHttp(7L, "/downloads", "https://example.com/old.zip", "local-default");
        RemoteDownloadTask oldActive = RemoteDownloadTask.createHttp(7L, "/downloads", "https://example.com/active.zip", "local-default");
        ReflectionTestUtils.setField(recent, "createdAt", Instant.now().minusSeconds(9 * 24 * 60 * 60));
        ReflectionTestUtils.setField(oldCompleted, "createdAt", Instant.now().minusSeconds(11 * 24 * 60 * 60));
        ReflectionTestUtils.setField(oldActive, "createdAt", Instant.now().minusSeconds(11 * 24 * 60 * 60));

        when(remoteDownloadTaskRepository.findActiveOrRecentByUserId(eq(7L), any(), any()))
                .thenReturn(List.of(oldActive, recent));

        assertThat(remoteDownloadService.listOwned(7L)).hasSize(2);
        verify(remoteDownloadTaskRepository).findActiveOrRecentByUserId(eq(7L), any(), any());
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
