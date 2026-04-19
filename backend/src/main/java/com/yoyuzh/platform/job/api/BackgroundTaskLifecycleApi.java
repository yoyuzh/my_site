package com.yoyuzh.platform.job.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.PageResponse;

import java.util.Map;
import java.util.Optional;

public interface BackgroundTaskLifecycleApi {

    PageResponse<BackgroundTaskView> listOwnedTasks(User user, int page, int size);

    BackgroundTaskView getOwnedTask(User user, Long id);

    BackgroundTaskView cancelOwnedTask(User user, Long id);

    BackgroundTaskView retryOwnedTask(User user, Long id);

    BackgroundTaskView createQueuedFileTask(User user,
                                            BackgroundTaskType type,
                                            Long fileId,
                                            String requestedPath,
                                            String correlationId);

    BackgroundTaskView createQueuedTask(User user,
                                        BackgroundTaskType type,
                                        Map<String, Object> publicState,
                                        Map<String, Object> privateState,
                                        String correlationId);

    Optional<BackgroundTaskView> createQueuedAutoMediaMetadataTask(Long userId,
                                                                   Long fileId,
                                                                   String correlationId);
}
