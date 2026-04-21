package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.platform.job.internal.application.BackgroundTaskService;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuntimeBackgroundTaskLifecycleApi implements BackgroundTaskLifecycleApi {

    private final BackgroundTaskService backgroundTaskService;

    @Override
    public PageResponse<BackgroundTaskView> listOwnedTasks(Long userId, int page, int size) {
        Page<BackgroundTask> result = backgroundTaskService.listOwnedTasks(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Override
    public BackgroundTaskView getOwnedTask(Long userId, Long id) {
        return toView(backgroundTaskService.getOwnedTask(userId, id));
    }

    @Override
    public BackgroundTaskView cancelOwnedTask(Long userId, Long id) {
        return toView(backgroundTaskService.cancelOwnedTask(userId, id));
    }

    @Override
    public BackgroundTaskView retryOwnedTask(Long userId, Long id) {
        return toView(backgroundTaskService.retryOwnedTask(userId, id));
    }

    @Override
    public BackgroundTaskView createQueuedFileTask(Long userId,
                                                   BackgroundTaskType type,
                                                   Long fileId,
                                                   String requestedPath,
                                                   String correlationId) {
        return toView(backgroundTaskService.createQueuedFileTask(userId, type, fileId, requestedPath, correlationId));
    }

    @Override
    public BackgroundTaskView createQueuedTask(Long userId,
                                               BackgroundTaskType type,
                                               Map<String, Object> publicState,
                                               Map<String, Object> privateState,
                                               String correlationId) {
        return toView(backgroundTaskService.createQueuedTaskByUserId(userId, type, publicState, privateState, correlationId));
    }

    @Override
    public BackgroundTaskView createQueuedTaskByUserId(Long userId,
                                                       BackgroundTaskType type,
                                                       Map<String, Object> publicState,
                                                       Map<String, Object> privateState,
                                                       String correlationId) {
        return toView(backgroundTaskService.createQueuedTaskByUserId(userId, type, publicState, privateState, correlationId));
    }

    @Override
    public Optional<BackgroundTaskView> createQueuedAutoMediaMetadataTask(Long userId,
                                                                          Long fileId,
                                                                          String correlationId) {
        return backgroundTaskService.createQueuedAutoMediaMetadataTask(userId, fileId, correlationId)
                .map(this::toView);
    }

    private BackgroundTaskView toView(BackgroundTask task) {
        return new BackgroundTaskView(
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getUserId(),
                task.getPublicStateJson(),
                task.getCorrelationId(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }
}
