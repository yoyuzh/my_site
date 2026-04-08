package com.yoyuzh.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.api.v2.ApiV2Exception;
import com.yoyuzh.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskServiceTest {

    @Mock
    private BackgroundTaskRepository backgroundTaskRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    private BackgroundTaskService backgroundTaskService;

    @BeforeEach
    void setUp() {
        backgroundTaskService = new BackgroundTaskService(backgroundTaskRepository, storedFileRepository, new ObjectMapper());
    }

    @Test
    void shouldRejectTaskCreationForForeignFile() {
        User user = createUser(7L);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user,
                BackgroundTaskType.ARCHIVE,
                99L,
                "/docs/foreign.txt",
                null
        )).isInstanceOf(ApiV2Exception.class)
                .hasMessage("file not found");
    }

    @Test
    void shouldRejectTaskCreationForDeletedFile() {
        User user = createUser(7L);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(100L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user,
                BackgroundTaskType.ARCHIVE,
                100L,
                "/docs/deleted.txt",
                null
        )).isInstanceOf(ApiV2Exception.class)
                .hasMessage("file not found");
    }

    @Test
    void shouldRejectTaskCreationWhenRequestedPathDoesNotMatchFile() {
        User user = createUser(7L);
        StoredFile file = createStoredFile(11L, user, "/docs", "real.txt", false, "text/plain", 3L);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(11L, 7L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user,
                BackgroundTaskType.ARCHIVE,
                11L,
                "/docs/fake.txt",
                null
        )).isInstanceOf(ApiV2Exception.class)
                .hasMessage("task path does not match file path");
    }

    @Test
    void shouldRejectExtractTaskForDirectory() {
        User user = createUser(7L);
        StoredFile directory = createStoredFile(12L, user, "/", "bundle", true, null, 0L);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(12L, 7L)).thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user,
                BackgroundTaskType.EXTRACT,
                12L,
                "/bundle",
                null
        )).isInstanceOf(ApiV2Exception.class)
                .hasMessage("task target type is not supported");
    }

    @Test
    void shouldRejectMediaMetadataTaskForNonMediaFile() {
        User user = createUser(7L);
        StoredFile file = createStoredFile(13L, user, "/docs", "notes.txt", false, "text/plain", 9L);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(13L, 7L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> backgroundTaskService.createQueuedFileTask(
                user,
                BackgroundTaskType.MEDIA_META,
                13L,
                "/docs/notes.txt",
                null
        )).isInstanceOf(ApiV2Exception.class)
                .hasMessage("media metadata task only supports media files");
    }

    @Test
    void shouldCreateTaskStateFromServerFilePath() {
        User user = createUser(7L);
        StoredFile file = createStoredFile(14L, user, "/docs", "photo.png", false, "image/png", 15L);
        when(storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(14L, 7L)).thenReturn(Optional.of(file));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask task = backgroundTaskService.createQueuedFileTask(
                user,
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
        assertThat(task.getPrivateStateJson()).contains("\"taskType\":\"MEDIA_META\"");
    }

    @Test
    void shouldClaimQueuedTaskOnlyWhenRepositoryTransitionSucceeds() {
        BackgroundTask task = createTask(1L, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskRepository.claimQueuedTask(
                eq(1L),
                eq(BackgroundTaskStatus.QUEUED),
                eq(BackgroundTaskStatus.RUNNING),
                any()
        )).thenReturn(1);
        when(backgroundTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        Optional<BackgroundTask> result = backgroundTaskService.claimQueuedTask(1L);

        assertThat(result).containsSame(task);
    }

    @Test
    void shouldNotClaimTaskWhenRepositoryTransitionWasSkipped() {
        when(backgroundTaskRepository.claimQueuedTask(
                eq(2L),
                eq(BackgroundTaskStatus.QUEUED),
                eq(BackgroundTaskStatus.RUNNING),
                any()
        )).thenReturn(0);

        Optional<BackgroundTask> result = backgroundTaskService.claimQueuedTask(2L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldCompleteRunningWorkerTaskAndMergePublicState() {
        BackgroundTask task = createTask(3L, BackgroundTaskStatus.RUNNING);
        task.setPublicStateJson("{\"fileId\":11}");
        when(backgroundTaskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskService.markWorkerTaskCompleted(3L, Map.of("worker", "noop"));

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.COMPLETED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getPublicStateJson()).contains("\"fileId\":11");
        assertThat(result.getPublicStateJson()).contains("\"worker\":\"noop\"");
    }

    @Test
    void shouldRecordWorkerFailureMessage() {
        BackgroundTask task = createTask(4L, BackgroundTaskStatus.RUNNING);
        when(backgroundTaskRepository.findById(4L)).thenReturn(Optional.of(task));
        when(backgroundTaskRepository.save(any(BackgroundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackgroundTask result = backgroundTaskService.markWorkerTaskFailed(4L, "media parser unavailable");

        assertThat(result.getStatus()).isEqualTo(BackgroundTaskStatus.FAILED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isEqualTo("media parser unavailable");
    }

    @Test
    void shouldFindQueuedTaskIdsInCreatedOrderLimit() {
        BackgroundTask first = createTask(5L, BackgroundTaskStatus.QUEUED);
        BackgroundTask second = createTask(6L, BackgroundTaskStatus.QUEUED);
        when(backgroundTaskRepository.findByStatusOrderByCreatedAtAsc(eq(BackgroundTaskStatus.QUEUED), any()))
                .thenReturn(List.of(first, second));

        List<Long> result = backgroundTaskService.findQueuedTaskIds(2);

        assertThat(result).containsExactly(5L, 6L);
    }

    private BackgroundTask createTask(Long id, BackgroundTaskStatus status) {
        BackgroundTask task = new BackgroundTask();
        task.setId(id);
        task.setType(BackgroundTaskType.MEDIA_META);
        task.setStatus(status);
        task.setUserId(7L);
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

    private StoredFile createStoredFile(Long id,
                                        User user,
                                        String path,
                                        String filename,
                                        boolean directory,
                                        String contentType,
                                        Long size) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(user);
        file.setPath(path);
        file.setFilename(filename);
        file.setDirectory(directory);
        file.setContentType(contentType);
        file.setSize(size);
        return file;
    }
}
