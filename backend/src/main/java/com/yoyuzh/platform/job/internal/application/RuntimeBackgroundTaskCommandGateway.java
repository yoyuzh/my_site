package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskService;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskCommandGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuntimeBackgroundTaskCommandGateway implements BackgroundTaskCommandGateway {

    private final BackgroundTaskService backgroundTaskService;

    @Override
    public BackgroundTask createQueuedFileTask(User user,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        return backgroundTaskService.createQueuedFileTask(user, type, fileId, requestedPath, correlationId);
    }

    @Override
    public Optional<BackgroundTask> createQueuedAutoMediaMetadataTask(Long userId,
                                                                      Long fileId,
                                                                      String correlationId) {
        return backgroundTaskService.createQueuedAutoMediaMetadataTask(userId, fileId, correlationId);
    }

    @Override
    public BackgroundTask createQueuedTask(User user,
                                           BackgroundTaskType type,
                                           Map<String, Object> publicState,
                                           Map<String, Object> privateState,
                                           String correlationId) {
        return backgroundTaskService.createQueuedTask(user, type, publicState, privateState, correlationId);
    }

    @Override
    public Page<BackgroundTask> listOwnedTasks(User user, Pageable pageable) {
        return backgroundTaskService.listOwnedTasks(user, pageable);
    }

    @Override
    public BackgroundTask getOwnedTask(User user, Long id) {
        return backgroundTaskService.getOwnedTask(user, id);
    }

    @Override
    public BackgroundTask cancelOwnedTask(User user, Long id) {
        return backgroundTaskService.cancelOwnedTask(user, id);
    }

    @Override
    public BackgroundTask retryOwnedTask(User user, Long id) {
        return backgroundTaskService.retryOwnedTask(user, id);
    }
}
