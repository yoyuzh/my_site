package com.yoyuzh.files.tasks;

import com.yoyuzh.auth.User;
import com.yoyuzh.platform.job.api.BackgroundTaskCommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskCommandServiceTest {

    @Mock
    private BackgroundTaskCommandGateway backgroundTaskCommandGateway;

    @InjectMocks
    private BackgroundTaskCommandService backgroundTaskCommandService;

    @Test
    void shouldDelegateQueuedFileTaskCreation() {
        User user = createUser(7L);
        BackgroundTask task = new BackgroundTask();
        when(backgroundTaskCommandGateway.createQueuedFileTask(user, BackgroundTaskType.ARCHIVE, 11L, "/docs/archive", "corr-1"))
                .thenReturn(task);

        BackgroundTask created = backgroundTaskCommandService.createQueuedFileTask(
                user,
                BackgroundTaskType.ARCHIVE,
                11L,
                "/docs/archive",
                "corr-1"
        );

        assertThat(created).isSameAs(task);
        verify(backgroundTaskCommandGateway).createQueuedFileTask(user, BackgroundTaskType.ARCHIVE, 11L, "/docs/archive", "corr-1");
    }

    @Test
    void shouldDelegateAutoMediaMetadataTaskCreation() {
        BackgroundTask task = new BackgroundTask();
        when(backgroundTaskCommandGateway.createQueuedAutoMediaMetadataTask(7L, 12L, "corr-2"))
                .thenReturn(Optional.of(task));

        Optional<BackgroundTask> created = backgroundTaskCommandService.createQueuedAutoMediaMetadataTask(7L, 12L, "corr-2");

        assertThat(created).containsSame(task);
        verify(backgroundTaskCommandGateway).createQueuedAutoMediaMetadataTask(7L, 12L, "corr-2");
    }

    @Test
    void shouldDelegateOwnedTaskOperations() {
        User user = createUser(8L);
        BackgroundTask task = new BackgroundTask();
        Page<BackgroundTask> page = new PageImpl<>(java.util.List.of(task));
        PageRequest pageable = PageRequest.of(0, 20);
        when(backgroundTaskCommandGateway.listOwnedTasks(user, pageable)).thenReturn(page);
        when(backgroundTaskCommandGateway.getOwnedTask(user, 1L)).thenReturn(task);
        when(backgroundTaskCommandGateway.cancelOwnedTask(user, 1L)).thenReturn(task);
        when(backgroundTaskCommandGateway.retryOwnedTask(user, 1L)).thenReturn(task);

        assertThat(backgroundTaskCommandService.listOwnedTasks(user, pageable)).isSameAs(page);
        assertThat(backgroundTaskCommandService.getOwnedTask(user, 1L)).isSameAs(task);
        assertThat(backgroundTaskCommandService.cancelOwnedTask(user, 1L)).isSameAs(task);
        assertThat(backgroundTaskCommandService.retryOwnedTask(user, 1L)).isSameAs(task);
    }

    @Test
    void shouldDelegateQueuedTaskCreation() {
        User user = createUser(9L);
        BackgroundTask task = new BackgroundTask();
        Map<String, Object> publicState = Map.of("phase", "queued");
        Map<String, Object> privateState = Map.of("taskType", "ARCHIVE");
        when(backgroundTaskCommandGateway.createQueuedTask(user, BackgroundTaskType.ARCHIVE, publicState, privateState, "corr-3"))
                .thenReturn(task);

        BackgroundTask created = backgroundTaskCommandService.createQueuedTask(
                user,
                BackgroundTaskType.ARCHIVE,
                publicState,
                privateState,
                "corr-3"
        );

        assertThat(created).isSameAs(task);
        verify(backgroundTaskCommandGateway).createQueuedTask(user, BackgroundTaskType.ARCHIVE, publicState, privateState, "corr-3");
    }

    private static User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        return user;
    }
}
