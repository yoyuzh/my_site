package com.yoyuzh.platform.job.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

public interface BackgroundTaskCommandGateway {

    BackgroundTask createQueuedFileTask(User user,
                                        BackgroundTaskType type,
                                        Long fileId,
                                        String requestedPath,
                                        String correlationId);

    Optional<BackgroundTask> createQueuedAutoMediaMetadataTask(Long userId,
                                                               Long fileId,
                                                               String correlationId);

    BackgroundTask createQueuedTask(User user,
                                    BackgroundTaskType type,
                                    Map<String, Object> publicState,
                                    Map<String, Object> privateState,
                                    String correlationId);

    Page<BackgroundTask> listOwnedTasks(User user, Pageable pageable);

    BackgroundTask getOwnedTask(User user, Long id);

    BackgroundTask cancelOwnedTask(User user, Long id);

    BackgroundTask retryOwnedTask(User user, Long id);
}
