package com.yoyuzh.files.tasks;

import com.yoyuzh.auth.User;
import com.yoyuzh.platform.job.api.BackgroundTaskCommandGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BackgroundTaskCommandService {

    private final BackgroundTaskCommandGateway backgroundTaskCommandGateway;

    public BackgroundTask createQueuedFileTask(User user,
                                               BackgroundTaskType type,
                                               Long fileId,
                                               String requestedPath,
                                               String correlationId) {
        return backgroundTaskCommandGateway.createQueuedFileTask(user, type, fileId, requestedPath, correlationId);
    }

    public Optional<BackgroundTask> createQueuedAutoMediaMetadataTask(Long userId,
                                                                      Long fileId,
                                                                      String correlationId) {
        return backgroundTaskCommandGateway.createQueuedAutoMediaMetadataTask(userId, fileId, correlationId);
    }

    public BackgroundTask createQueuedTask(User user,
                                           BackgroundTaskType type,
                                           Map<String, Object> publicState,
                                           Map<String, Object> privateState,
                                           String correlationId) {
        return backgroundTaskCommandGateway.createQueuedTask(user, type, publicState, privateState, correlationId);
    }

    public Page<BackgroundTask> listOwnedTasks(User user, Pageable pageable) {
        return backgroundTaskCommandGateway.listOwnedTasks(user, pageable);
    }

    public BackgroundTask getOwnedTask(User user, Long id) {
        return backgroundTaskCommandGateway.getOwnedTask(user, id);
    }

    public BackgroundTask cancelOwnedTask(User user, Long id) {
        return backgroundTaskCommandGateway.cancelOwnedTask(user, id);
    }

    public BackgroundTask retryOwnedTask(User user, Long id) {
        return backgroundTaskCommandGateway.retryOwnedTask(user, id);
    }
}
