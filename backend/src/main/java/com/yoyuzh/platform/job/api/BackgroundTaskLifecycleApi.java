package com.yoyuzh.platform.job.api;

import com.yoyuzh.shared.kernel.PageResponse;

import java.util.Map;
import java.util.Optional;

public interface BackgroundTaskLifecycleApi {

    PageResponse<BackgroundTaskView> listOwnedTasks(Long userId, int page, int size);

    BackgroundTaskView getOwnedTask(Long userId, Long id);

    TaskProgressResponse getOwnedTaskProgress(Long userId, Long id);

    BackgroundTaskView cancelOwnedTask(Long userId, Long id);

    BackgroundTaskView retryOwnedTask(Long userId, Long id);

    BackgroundTaskView createQueuedFileTask(Long userId,
                                            BackgroundTaskType type,
                                            Long fileId,
                                            String requestedPath,
                                            String correlationId);

    BackgroundTaskView createQueuedTask(Long userId,
                                        BackgroundTaskType type,
                                        Map<String, Object> publicState,
                                        Map<String, Object> privateState,
                                        String correlationId);

    BackgroundTaskView createQueuedTaskByUserId(Long userId,
                                                BackgroundTaskType type,
                                                Map<String, Object> publicState,
                                                Map<String, Object> privateState,
                                                String correlationId);

    BackgroundTaskView createSearchIndexRebuildTask(Long requestedByUserId);

    Optional<BackgroundTaskView> createQueuedAutoMediaMetadataTask(Long userId,
                                                                   Long fileId,
                                                                   String correlationId);
}
