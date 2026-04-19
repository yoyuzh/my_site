package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskService;
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
    public PageResponse<BackgroundTaskView> listOwnedTasks(User user, int page, int size) {
        Page<BackgroundTask> result = backgroundTaskService.listOwnedTasks(
                user,
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
    public BackgroundTaskView getOwnedTask(User user, Long id) {
        return toView(backgroundTaskService.getOwnedTask(user, id));
    }

    @Override
    public BackgroundTaskView cancelOwnedTask(User user, Long id) {
        return toView(backgroundTaskService.cancelOwnedTask(user, id));
    }

    @Override
    public BackgroundTaskView retryOwnedTask(User user, Long id) {
        return toView(backgroundTaskService.retryOwnedTask(user, id));
    }

    @Override
    public BackgroundTaskView createQueuedFileTask(User user,
                                                   BackgroundTaskType type,
                                                   Long fileId,
                                                   String requestedPath,
                                                   String correlationId) {
        return toView(backgroundTaskService.createQueuedFileTask(user, type, fileId, requestedPath, correlationId));
    }

    @Override
    public BackgroundTaskView createQueuedTask(User user,
                                               BackgroundTaskType type,
                                               Map<String, Object> publicState,
                                               Map<String, Object> privateState,
                                               String correlationId) {
        return toView(backgroundTaskService.createQueuedTask(user, type, publicState, privateState, correlationId));
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
