package com.yoyuzh.files.tasks;

import com.yoyuzh.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BackgroundTaskCommandService {

    private final BackgroundTaskService backgroundTaskService;

    public BackgroundTask createQueuedFileTask(User user,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        return backgroundTaskService.createQueuedFileTask(user, type, fileId, requestedPath, correlationId);
    }

    public Optional<BackgroundTask> createQueuedAutoMediaMetadataTask(Long userId,
                                                                      Long fileId,
                                                                      String correlationId) {
        return backgroundTaskService.createQueuedAutoMediaMetadataTask(userId, fileId, correlationId);
    }

    public BackgroundTask createQueuedTask(User user,
                                           BackgroundTaskType type,
                                           Map<String, Object> publicState,
                                           Map<String, Object> privateState,
                                           String correlationId) {
        return backgroundTaskService.createQueuedTask(user, type, publicState, privateState, correlationId);
    }

    public Page<BackgroundTask> listOwnedTasks(User user, Pageable pageable) {
        return backgroundTaskService.listOwnedTasks(user, pageable);
    }

    public BackgroundTask getOwnedTask(User user, Long id) {
        return backgroundTaskService.getOwnedTask(user, id);
    }

    public BackgroundTask cancelOwnedTask(User user, Long id) {
        return backgroundTaskService.cancelOwnedTask(user, id);
    }

    public BackgroundTask retryOwnedTask(User user, Long id) {
        return backgroundTaskService.retryOwnedTask(user, id);
    }
}
