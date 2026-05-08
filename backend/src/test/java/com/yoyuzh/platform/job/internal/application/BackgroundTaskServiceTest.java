package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.TaskProgressResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskServiceTest {

    @Mock
    private BackgroundTaskRepository backgroundTaskRepository;

    @Mock
    private WorkspaceFileQueryApi workspaceFileQueryApi;

    @Mock
    private DistributedLockGateway distributedLockGateway;

    private BackgroundTaskService backgroundTaskService;
    private BackgroundTaskExecutionService backgroundTaskExecutionService;

    @BeforeEach
    void setUp() {
        backgroundTaskService = new BackgroundTaskService(
                backgroundTaskRepository,
                workspaceFileQueryApi,
                new ObjectMapper(),
                distributedLockGateway
        );
        backgroundTaskExecutionService = new BackgroundTaskExecutionService(
                backgroundTaskRepository,
                new BackgroundTaskRetryPolicy(),
                new BackgroundTaskStateManager(new ObjectMapper())
        );
        lenient().when(distributedLockGateway.executeWithLock(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> action = (Supplier<Object>) invocation.getArgument(2);
            return action.get();
        });
    }

    @Test
    void shouldRejectTaskCreationForForeignFile() {
        User user = createUser(7L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.ARCHIVE,
                99L,
                "/docs/foreign.txt",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("file not found");
    }

    @Test
    void shouldUseTaskNotFoundErrorCodeForOwnedTaskLookup() {
        when(backgroundTaskRepository.findByIdAndUserId(404L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backgroundTaskService.getOwnedTask(7L, 404L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    void shouldRejectTaskCreationForDeletedFile() {
        User user = createUser(7L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.ARCHIVE,
                100L,
                "/docs/deleted.txt",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("file not found");
    }

    @Test
    void shouldRejectTaskCreationWhenRequestedPathDoesNotMatchFile() {
        User user = createUser(7L);
        WorkspaceFileSnapshot file = createStoredFile(11L, user, "/docs", "real.txt", false, "text/plain", 3L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 11L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.ARCHIVE,
                11L,
                "/docs/fake.txt",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("task path does not match file path");
    }

    @Test
    void shouldRejectExtractTaskForDirectory() {
        User user = createUser(7L);
        WorkspaceFileSnapshot directory = createStoredFile(12L, user, "/", "bundle", true, null, 0L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 12L)).thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.EXTRACT,
                12L,
                "/bundle",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("task target type is not supported");
    }

    @Test
    void shouldRejectExtractTaskForUnsupportedArchive() {
        User user = createUser(7L);
        WorkspaceFileSnapshot archive = createStoredFile(17L, user, "/docs", "backup.exe", false, "application/octet-stream", 64L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 17L)).thenReturn(Optional.of(archive));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.EXTRACT,
                17L,
                "/docs/backup.exe",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("extract task only supports supported archive files");
    }

    @Test
    void shouldRejectMediaMetadataTaskForNonMediaFile() {
        User user = createUser(7L);
        WorkspaceFileSnapshot file = createStoredFile(13L, user, "/docs", "notes.txt", false, "text/plain", 9L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 13L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.MEDIA_META,
                13L,
                "/docs/notes.txt",
                null
        )).isInstanceOf(BusinessException.class)
                .hasMessage("media metadata task only supports media files");
    }

    @Test
    void shouldCreateTaskStateFromServerFilePath() {
        User user = createUser(7L);
        WorkspaceFileSnapshot file = createStoredFile(14L, user, "/docs", "photo.png", false, "image/png", 15L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 14L)).thenReturn(Optional.of(file));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask task = backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.MEDIA_META,
                14L,
                "/docs/photo.png",
                "media-1"
        );

        assertThat(task.getPublicStateJson()).contains("\"fileId\":14");
        assertThat(task.getPublicStateJson()).contains("\"path\":\"/docs/photo.png\"");
        assertThat(task.getPublicStateJson()).contains("\"filename\":\"photo.png\"");
        assertThat(task.getPublicStateJson()).contains("\"directory\":false");
        assertThat(task.getPublicStateJson()).contains("\"contentType\":\"image/png\"");
        assertThat(task.getPublicStateJson()).contains("\"size\":15");
        assertThat(task.getPublicStateJson()).contains("\"phase\":\"queued\"");
        assertThat(task.getPublicStateJson()).contains("\"attemptCount\":0");
        assertThat(task.getPublicStateJson()).contains("\"maxAttempts\":2");
        assertThat(task.getPrivateStateJson()).contains("\"taskType\":\"MEDIA_META\"");
    }

    @Test
    void shouldCreateArchiveTaskStateWithDerivedOutputTarget() {
        User user = createUser(7L);
        WorkspaceFileSnapshot directory = createStoredFile(15L, user, "/docs", "archive", true, null, 0L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 15L)).thenReturn(Optional.of(directory));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask task = backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.ARCHIVE,
                15L,
                "/docs/archive",
                "archive-1"
        );

        assertThat(task.getPublicStateJson()).contains("\"outputPath\":\"/docs\"");
        assertThat(task.getPublicStateJson()).contains("\"outputFilename\":\"archive.zip\"");
        assertThat(task.getPublicStateJson()).contains("\"maxAttempts\":4");
        assertThat(task.getPrivateStateJson()).contains("\"outputPath\":\"/docs\"");
        assertThat(task.getPrivateStateJson()).contains("\"outputFilename\":\"archive.zip\"");
    }

    @Test
    void shouldCreateExtractTaskStateWithDerivedOutputTarget() {
        User user = createUser(7L);
        WorkspaceFileSnapshot archive = createStoredFile(16L, user, "/docs", "extract.zip", false, "application/zip", 32L);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 16L)).thenReturn(Optional.of(archive));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask task = backgroundTaskService.createQueuedFileTask(
                user.getId(),
                BackgroundTaskType.EXTRACT,
                16L,
                "/docs/extract.zip",
                "extract-1"
        );

        assertThat(task.getPublicStateJson()).contains("\"outputPath\":\"/docs\"");
        assertThat(task.getPublicStateJson()).contains("\"outputDirectoryName\":\"extract\"");
        assertThat(task.getPublicStateJson()).contains("\"maxAttempts\":3");
        assertThat(task.getPrivateStateJson()).contains("\"outputPath\":\"/docs\"");
        assertThat(task.getPrivateStateJson()).contains("\"outputDirectoryName\":\"extract\"");
    }

    @Test
    void shouldClaimQueuedTaskOnlyWhenRepositoryTransitionSucceeds() {
        BackgroundTask task = createTask(1L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskRepository.claimQueuedTask(
                eq(1L),
                eq(BackgroundTaskStatus.QUEUED),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<BackgroundTask> result = backgroundTaskExecutionService.claimQueuedTask(1L, "worker-a", 120L);

        assertThat(result).containsSame(task);
        assertThat(result.orElseThrow().getLeaseOwner()).isEqualTo("worker-a");
        assertThat(result.orElseThrow().getLeaseExpiresAt()).isNotNull();
        assertThat(result.orElseThrow().getHeartbeatAt()).isNotNull();
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"phase\":\"running\"");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"attemptCount\":1");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"maxAttempts\":4");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"workerOwner\":\"worker-a\"");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"heartbeatAt\":");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"leaseExpiresAt\":");
    }

    @Test
    void shouldNotClaimTaskWhenRepositoryTransitionWasSkipped() {
        when(backgroundTaskRepository.claimQueuedTask(
                eq(2L),
                eq(BackgroundTaskStatus.QUEUED),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(0);

        Optional<BackgroundTask> result = backgroundTaskExecutionService.claimQueuedTask(2L, "worker-a", 120L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldCompleteRunningWorkerTaskAndMergePublicState() {
        BackgroundTask task = createTask(3L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(120));
        task.setHeartbeatAt(java.time.LocalDateTime.now());
        task.setPublicStateJson("{\"fileId\":11,\"phase\":\"archiving\"}");
        when(backgroundTaskRepository.refreshRunningTaskLease(
                eq(3L),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskExecutionService.markWorkerTaskCompleted(3L, "worker-a", Map.of("worker", "noop"), 120L);

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.COMPLETED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getLeaseOwner()).isNull();
        assertThat(result.getLeaseExpiresAt()).isNull();
        assertThat(result.getHeartbeatAt()).isNull();
        assertThat(result.getPublicStateJson()).contains("\"fileId\":11");
        assertThat(result.getPublicStateJson()).contains("\"worker\":\"noop\"");
        assertThat(result.getPublicStateJson()).contains("\"phase\":\"completed\"");
        assertThat(result.getPublicStateJson()).contains("\"heartbeatAt\":\"" + result.getFinishedAt() + "\"");
        assertThat(result.getPublicStateJson()).doesNotContain("workerOwner");
        assertThat(result.getPublicStateJson()).doesNotContain("leaseExpiresAt");
    }

    @Test
    void shouldMergeWorkerProgressStateWhileTaskIsRunning() {
        BackgroundTask task = createTask(7L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.RUNNING);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(120));
        task.setHeartbeatAt(java.time.LocalDateTime.now());
        task.setPublicStateJson("{\"fileId\":11,\"phase\":\"running\"}");
        when(backgroundTaskRepository.refreshRunningTaskLease(
                eq(7L),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskExecutionService.markWorkerTaskProgress(
                7L,
                "worker-a",
                Map.of("phase", "extracting", "progressPercent", 50),
                120L
        );

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.RUNNING);
        assertThat(result.getPublicStateJson()).contains("\"phase\":\"extracting\"");
        assertThat(result.getPublicStateJson()).contains("\"progressPercent\":50");
        assertThat(result.getPublicStateJson()).contains("\"workerOwner\":\"worker-a\"");
        assertThat(result.getPublicStateJson()).contains("\"heartbeatAt\":");
        assertThat(result.getPublicStateJson()).contains("\"leaseExpiresAt\":");
    }

    @Test
    void shouldRecordTerminalWorkerFailureMessageWhenFailureIsNotRetryable() {
        BackgroundTask task = createTask(4L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.RUNNING);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(120));
        task.setHeartbeatAt(java.time.LocalDateTime.now());
        task.setPublicStateJson("{\"fileId\":11,\"phase\":\"extracting\"}");
        when(backgroundTaskRepository.refreshRunningTaskLease(
                eq(4L),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(4L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskExecutionService.markWorkerTaskFailed(
                4L,
                "worker-a",
                "media parser unavailable",
                BackgroundTaskFailureCategory.DATA_STATE,
                120L
        );

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.FAILED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isEqualTo("media parser unavailable");
        assertThat(result.getLeaseOwner()).isNull();
        assertThat(result.getLeaseExpiresAt()).isNull();
        assertThat(result.getHeartbeatAt()).isNull();
        assertThat(result.getPublicStateJson()).contains("\"phase\":\"failed\"");
        assertThat(result.getPublicStateJson()).contains("\"attemptCount\":1");
        assertThat(result.getPublicStateJson()).contains("\"maxAttempts\":3");
        assertThat(result.getPublicStateJson()).contains("\"failureCategory\":\"DATA_STATE\"");
        assertThat(result.getPublicStateJson()).doesNotContain("retryScheduled");
    }

    @Test
    void shouldRequeueRetryableWorkerFailureWithBackoff() {
        BackgroundTask task = createTask(14L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(120));
        task.setHeartbeatAt(java.time.LocalDateTime.now());
        task.setPublicStateJson("{\"fileId\":11,\"phase\":\"extracting\"}");
        when(backgroundTaskRepository.refreshRunningTaskLease(
                eq(14L),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(14L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskExecutionService.markWorkerTaskFailed(
                14L,
                "worker-a",
                "storage timeout",
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                120L
        );

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(result.getFinishedAt()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getNextRunAt()).isNotNull();
        assertThat(result.getLeaseOwner()).isNull();
        assertThat(result.getLeaseExpiresAt()).isNull();
        assertThat(result.getHeartbeatAt()).isNull();
        assertThat(result.getPublicStateJson()).contains("\"phase\":\"queued\"");
        assertThat(result.getPublicStateJson()).contains("\"retryScheduled\":true");
        assertThat(result.getPublicStateJson()).contains("\"retryDelaySeconds\":30");
        assertThat(result.getPublicStateJson()).contains("\"failureCategory\":\"TRANSIENT_INFRASTRUCTURE\"");
        assertThat(result.getPublicStateJson()).contains("\"lastFailureMessage\":\"storage timeout\"");
    }

    @Test
    void shouldUseLongerBackoffForRateLimitedFailures() {
        BackgroundTask task = createTask(18L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.RUNNING);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(120));
        task.setHeartbeatAt(java.time.LocalDateTime.now());
        task.setPublicStateJson("{\"fileId\":11,\"phase\":\"archiving\"}");
        when(backgroundTaskRepository.refreshRunningTaskLease(
                eq(18L),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(18L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskExecutionService.markWorkerTaskFailed(
                18L,
                "worker-a",
                "429 too many requests",
                BackgroundTaskFailureCategory.RATE_LIMITED,
                120L
        );

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(result.getPublicStateJson()).contains("\"retryDelaySeconds\":120");
        assertThat(result.getPublicStateJson()).contains("\"failureCategory\":\"RATE_LIMITED\"");
    }

    @Test
    void shouldFailTerminallyWhenRetryableFailureExhaustsAttempts() {
        BackgroundTask task = createTask(15L, BackgroundTaskType.MEDIA_META, BackgroundTaskStatus.RUNNING);
        task.setAttemptCount(2);
        task.setMaxAttempts(2);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(120));
        task.setHeartbeatAt(java.time.LocalDateTime.now());
        task.setPublicStateJson("{\"fileId\":11,\"phase\":\"extracting-metadata\",\"attemptCount\":2,\"maxAttempts\":2}");
        when(backgroundTaskRepository.refreshRunningTaskLease(
                eq(15L),
                eq(BackgroundTaskStatus.RUNNING),
                eq("worker-a"),
                any(),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(15L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskExecutionService.markWorkerTaskFailed(
                15L,
                "worker-a",
                "storage timeout",
                BackgroundTaskFailureCategory.TRANSIENT_INFRASTRUCTURE,
                120L
        );

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.FAILED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isEqualTo("storage timeout");
        assertThat(result.getPublicStateJson()).contains("\"phase\":\"failed\"");
        assertThat(result.getPublicStateJson()).contains("\"failureCategory\":\"TRANSIENT_INFRASTRUCTURE\"");
        assertThat(result.getPublicStateJson()).doesNotContain("retryScheduled");
    }

    @Test
    void shouldRetryFailedTaskAndResetPublicState() {
        BackgroundTask task = createTask(8L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.FAILED);
        task.setPublicStateJson("""
                {"fileId":11,"path":"/docs/extract.zip","phase":"failed","worker":"extract","processedFileCount":1,"totalFileCount":2}
                """);
        task.setPrivateStateJson("""
                {
                  "fileId":11,
                  "path":"/docs/extract.zip",
                  "taskType":"EXTRACT",
                  "outputPath":"/docs",
                  "outputDirectoryName":"extract",
                  "_publicStateSeed":{
                    "fileId":11,
                    "path":"/docs/extract.zip",
                    "outputPath":"/docs",
                    "outputDirectoryName":"extract"
                  }
                }
                """);
        task.setFinishedAt(java.time.LocalDateTime.now());
        task.setErrorMessage("extract task only supports supported archive files");
        BackgroundTask reloaded = createTask(8L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.QUEUED);
        reloaded.setPublicStateJson("""
                {"fileId":11,"path":"/docs/extract.zip","outputPath":"/docs","outputDirectoryName":"extract","phase":"queued","attemptCount":0,"maxAttempts":3}
                """);
        when(backgroundTaskRepository.findByIdAndUserId(8L, 7L)).thenReturn(Optional.of(task), Optional.of(reloaded));
        when(backgroundTaskRepository.retryOwnedTask(
                eq(8L),
                eq(7L),
                eq(task.getUpdatedAt()),
                eq(BackgroundTaskStatus.FAILED),
                eq(BackgroundTaskStatus.QUEUED),
                anyString(),
                any()
        )).thenReturn(1);

        BackgroundTask result = backgroundTaskService.retryOwnedTask(7L, 8L);

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(result.getPublicStateJson()).contains("\"phase\":\"queued\"");
        assertThat(result.getPublicStateJson()).contains("\"outputPath\":\"/docs\"");
        assertThat(result.getPublicStateJson()).contains("\"outputDirectoryName\":\"extract\"");
        assertThat(result.getPublicStateJson()).contains("\"attemptCount\":0");
        assertThat(result.getPublicStateJson()).contains("\"maxAttempts\":3");
        assertThat(result.getPublicStateJson()).doesNotContain("taskType");
        assertThat(result.getPublicStateJson()).doesNotContain("worker");
        assertThat(result.getPublicStateJson()).doesNotContain("processedFileCount");
        assertThat(result.getPublicStateJson()).doesNotContain("totalFileCount");
        verify(backgroundTaskRepository, never()).save(any(BackgroundTask.class));
    }

    @Test
    void shouldNotLeakRemoteDownloadIdWhenRetryingTask() {
        BackgroundTask task = createTask(18L, BackgroundTaskType.REMOTE_DOWNLOAD, BackgroundTaskStatus.FAILED);
        task.setPublicStateJson("""
                {"phase":"failed","message":"remote download failed"}
                """);
        task.setPrivateStateJson("""
                {
                  "taskType":"REMOTE_DOWNLOAD",
                  "remoteDownloadId":42,
                  "_publicStateSeed":{
                    "message":"remote download queued",
                    "sourceType":"HTTP"
                  }
                }
                """);
        BackgroundTask reloaded = createTask(18L, BackgroundTaskType.REMOTE_DOWNLOAD, BackgroundTaskStatus.QUEUED);
        reloaded.setPublicStateJson("""
                {"message":"remote download queued","sourceType":"HTTP","phase":"queued","attemptCount":0,"maxAttempts":3}
                """);
        when(backgroundTaskRepository.findByIdAndUserId(18L, 7L)).thenReturn(Optional.of(task), Optional.of(reloaded));
        when(backgroundTaskRepository.retryOwnedTask(
                eq(18L),
                eq(7L),
                eq(task.getUpdatedAt()),
                eq(BackgroundTaskStatus.FAILED),
                eq(BackgroundTaskStatus.QUEUED),
                anyString(),
                any()
        )).thenReturn(1);

        BackgroundTask result = backgroundTaskService.retryOwnedTask(7L, 18L);

        assertThat(result.getPublicStateJson()).doesNotContain("remoteDownloadId");
    }

    @Test
    void shouldRejectRetryForNonFailedTask() {
        BackgroundTask task = createTask(9L, BackgroundTaskType.ARCHIVE, BackgroundTaskStatus.COMPLETED);
        when(backgroundTaskRepository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> backgroundTaskService.retryOwnedTask(7L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("only failed tasks can be retried");
    }

    @Test
    void shouldReadOwnedTaskProgressFromPublicState() {
        User user = createUser(7L);
        BackgroundTask task = createTask(22L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.RUNNING);
        task.setPublicStateJson("""
                {"phase":"extracting","processedFileCount":2,"totalFileCount":3,"processedDirectoryCount":1,"totalDirectoryCount":1,"message":"extracting nested files"}
                """);
        when(backgroundTaskRepository.findByIdAndUserId(22L, 7L)).thenReturn(Optional.of(task));

        TaskProgressResponse response = backgroundTaskService.getOwnedTaskProgress(user.getId(), 22L);

        assertThat(response.taskId()).isEqualTo(22L);
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.processedItems()).isEqualTo(3L);
        assertThat(response.totalItems()).isEqualTo(4L);
        assertThat(response.progressPercent()).isEqualTo(75);
        assertThat(response.message()).isEqualTo("extracting nested files");
    }

    @Test
    void shouldCreateSearchIndexRebuildTaskWithInitialProgressState() {
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask task = backgroundTaskService.createSearchIndexRebuildTask(7L);

        assertThat(task.getType()).isEqualTo(BackgroundTaskType.SEARCH_INDEX_REBUILD);
        assertThat(task.getUserId()).isEqualTo(7L);
        assertThat(task.getCorrelationId()).startsWith("search-index-rebuild:");
        assertThat(task.getPublicStateJson()).contains("\"message\":\"search index rebuild queued\"");
        assertThat(task.getPublicStateJson()).contains("\"processedItems\":0");
        assertThat(task.getPublicStateJson()).contains("\"totalItems\":1");
        assertThat(task.getPublicStateJson()).contains("\"progressPercent\":0");
        assertThat(task.getPublicStateJson()).contains("\"phase\":\"queued\"");
    }

    @Test
    void shouldRequeueOnlyExpiredRunningTasksOnStartup() {
        BackgroundTask expired = createTask(10L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.RUNNING);
        expired.setLeaseOwner("worker-stale");
        expired.setLeaseExpiresAt(java.time.LocalDateTime.now().minusSeconds(5));
        expired.setHeartbeatAt(java.time.LocalDateTime.now().minusSeconds(10));
        expired.setPublicStateJson("""
                {"fileId":11,"path":"/docs/extract.zip","phase":"extracting","worker":"extract","workerOwner":"worker-stale"}
                """);
        expired.setPrivateStateJson("""
                {
                  "fileId":11,
                  "path":"/docs/extract.zip",
                  "taskType":"EXTRACT",
                  "outputPath":"/docs",
                  "outputDirectoryName":"extract",
                  "_publicStateSeed":{
                    "fileId":11,
                    "path":"/docs/extract.zip",
                    "outputPath":"/docs",
                    "outputDirectoryName":"extract"
                  }
                }
                """);
        expired.setFinishedAt(java.time.LocalDateTime.now());
        expired.setErrorMessage("partial failure");
        BackgroundTask fresh = createTask(11L, BackgroundTaskType.EXTRACT, BackgroundTaskStatus.RUNNING);
        fresh.setLeaseOwner("worker-live");
        fresh.setLeaseExpiresAt(java.time.LocalDateTime.now().plusSeconds(300));
        fresh.setHeartbeatAt(java.time.LocalDateTime.now());
        when(backgroundTaskRepository.findExpiredRunningTaskIds(eq(BackgroundTaskStatus.RUNNING), any(), any()))
                .thenReturn(List.of(10L));
        when(backgroundTaskRepository.requeueExpiredRunningTask(
                eq(10L),
                eq(BackgroundTaskStatus.RUNNING),
                eq(BackgroundTaskStatus.QUEUED),
                any(),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(10L)).thenReturn(Optional.of(expired));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int recovered = backgroundTaskExecutionService.requeueExpiredRunningTasks();

        assertThat(recovered).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(expired.getFinishedAt()).isNull();
        assertThat(expired.getErrorMessage()).isNull();
        assertThat(expired.getLeaseOwner()).isNull();
        assertThat(expired.getLeaseExpiresAt()).isNull();
        assertThat(expired.getHeartbeatAt()).isNull();
        assertThat(expired.getPublicStateJson()).contains("\"phase\":\"queued\"");
        assertThat(expired.getPublicStateJson()).contains("\"attemptCount\":1");
        assertThat(expired.getPublicStateJson()).contains("\"maxAttempts\":3");
        assertThat(expired.getPublicStateJson()).contains("\"outputPath\":\"/docs\"");
        assertThat(expired.getPublicStateJson()).contains("\"outputDirectoryName\":\"extract\"");
        assertThat(expired.getPublicStateJson()).doesNotContain("worker");
        assertThat(expired.getPublicStateJson()).doesNotContain("taskType");
        assertThat(expired.getPublicStateJson()).doesNotContain("workerOwner");
        assertThat(fresh.getStatus()).isEqualTo(BackgroundTaskStatus.RUNNING);
    }

    @Test
    void shouldFindQueuedTaskIdsInCreatedOrderLimit() {
        when(backgroundTaskRepository.findReadyTaskIdsByStatusOrder(eq(BackgroundTaskStatus.QUEUED), any(), any()))
                .thenReturn(List.of(5L, 6L));

        List<Long> result = backgroundTaskExecutionService.findQueuedTaskIds(2);

        assertThat(result).containsExactly(5L, 6L);
    }

    @Test
    void shouldReturnEmptyTaskIdsWhenLimitIsNonPositive() {
        List<Long> result = backgroundTaskExecutionService.findQueuedTaskIds(0);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldCreateAutoMediaMetadataTaskWhenCorrelationIsNew() {
        WorkspaceFileSnapshot file = createStoredFile(19L, createUser(7L), "/docs", "photo.png", false, "image/png", 18L);
        when(backgroundTaskRepository.existsByCorrelationId("media-meta:auto:file:19")).thenReturn(false);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 19L)).thenReturn(Optional.of(file));
        when(backgroundTaskRepository.saveAndFlush(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<BackgroundTask> result = backgroundTaskService.createQueuedAutoMediaMetadataTask(
                7L,
                19L,
                "media-meta:auto:file:19"
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getType()).isEqualTo(BackgroundTaskType.MEDIA_META);
        assertThat(result.orElseThrow().getCorrelationId()).isEqualTo("media-meta:auto:file:19");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"path\":\"/docs/photo.png\"");
        assertThat(result.orElseThrow().getPublicStateJson()).contains("\"phase\":\"queued\"");
        verify(distributedLockGateway).executeWithLock(eq("background-task-correlation:media-meta:auto:file:19"), any(), any());
    }

    @Test
    void shouldSkipAutoMediaMetadataTaskWhenCorrelationAlreadyExists() {
        when(backgroundTaskRepository.existsByCorrelationId("media-meta:auto:file:20")).thenReturn(true);

        Optional<BackgroundTask> result = backgroundTaskService.createQueuedAutoMediaMetadataTask(
                7L,
                20L,
                "media-meta:auto:file:20"
        );

        assertThat(result).isEmpty();
        verify(workspaceFileQueryApi, never()).findOwnedActiveFile(7L, 20L);
        verify(backgroundTaskRepository, never()).save(any(BackgroundTask.class));
    }

    @Test
    void shouldTreatDuplicateCorrelationInsertAsIdempotentNoOp() {
        WorkspaceFileSnapshot file = createStoredFile(21L, createUser(7L), "/docs", "photo.png", false, "image/png", 18L);
        when(backgroundTaskRepository.existsByCorrelationId("media-meta:auto:file:21")).thenReturn(false);
        when(workspaceFileQueryApi.findOwnedActiveFile(7L, 21L)).thenReturn(Optional.of(file));
        when(backgroundTaskRepository.saveAndFlush(any(BackgroundTask.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate correlation id"));

        Optional<BackgroundTask> result = backgroundTaskService.createQueuedAutoMediaMetadataTask(
                7L,
                21L,
                "media-meta:auto:file:21"
        );

        assertThat(result).isEmpty();
    }

    private BackgroundTask createTask(Long id, BackgroundTaskType type, BackgroundTaskStatus status) {
        BackgroundTask task = new BackgroundTask();
        task.setId(id);
        task.setType(type);
        task.setStatus(status);
        task.setUserId(7L);
        task.setAttemptCount(status == BackgroundTaskStatus.RUNNING ? 1 : 0);
        task.setMaxAttempts(switch (type) {
            case ARCHIVE -> 4;
            case EXTRACT -> 3;
            case MEDIA_META -> 2;
            case SEARCH_INDEX_REBUILD -> 1;
            default -> 1;
        });
        task.setPublicStateJson("{}");
        task.setPrivateStateJson("{}");
        return task;
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        return user;
    }

    private WorkspaceFileSnapshot createStoredFile(Long id,
                                                   User user,
                                                   String path,
                                                   String filename,
                                                   boolean directory,
                                                   String contentType,
                                                   Long size) {
        return new WorkspaceFileSnapshot(
                id,
                user.getId(),
                filename,
                path,
                size,
                contentType,
                directory,
                null,
                java.time.LocalDateTime.now()
        );
    }
}
