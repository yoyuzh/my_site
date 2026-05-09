package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.api.FileDeleteMode;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMutationTaskApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationTaskView;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RuntimeWorkspaceMutationTaskApi implements WorkspaceMutationTaskApi {

    private final BackgroundTaskService backgroundTaskService;
    private final BackgroundTaskWorker backgroundTaskWorker;

    public RuntimeWorkspaceMutationTaskApi(BackgroundTaskService backgroundTaskService,
                                           BackgroundTaskWorker backgroundTaskWorker) {
        this.backgroundTaskService = backgroundTaskService;
        this.backgroundTaskWorker = backgroundTaskWorker;
    }

    @Override
    public WorkspaceMutationTaskView enqueueRename(Long userId, Long fileId, String filename) {
        Long safeUserId = requiredId(userId, "workspace mutation task missing userId");
        Long safeFileId = requiredId(fileId, "rename task missing fileId");
        String safeFilename = requiredText(filename, "rename task missing filename");
        Map<String, Object> state = baseState("RENAME", List.of(safeFileId), "重命名任务已提交");
        state.put("fileId", safeFileId);
        state.put("filename", safeFilename);
        BackgroundTask task = backgroundTaskService.createQueuedTaskByUserId(
                safeUserId,
                BackgroundTaskType.WORKSPACE_MUTATION,
                state,
                privateState(state),
                correlationId("rename")
        );
        backgroundTaskWorker.wakeLightweightTasks();
        return toView(task);
    }

    @Override
    public WorkspaceMutationTaskView enqueueMove(Long userId,
                                                 List<Long> fileIds,
                                                 String targetPath,
                                                 WorkspaceMoveConflictStrategy conflictStrategy) {
        Long safeUserId = requiredId(userId, "workspace mutation task missing userId");
        List<Long> safeFileIds = requiredFileIds(fileIds, "move task missing fileIds");
        String safeTargetPath = requiredText(targetPath, "move task missing targetPath");
        Map<String, Object> state = baseState("MOVE", safeFileIds, "移动任务已提交");
        state.put("targetPath", safeTargetPath);
        if (conflictStrategy != null) {
            state.put("conflictStrategy", conflictStrategy.name());
        }
        BackgroundTask task = backgroundTaskService.createQueuedTaskByUserId(
                safeUserId,
                BackgroundTaskType.WORKSPACE_MUTATION,
                state,
                privateState(state),
                correlationId("move")
        );
        backgroundTaskWorker.wakeLightweightTasks();
        return toView(task);
    }

    @Override
    public WorkspaceMutationTaskView enqueueDelete(Long userId, List<Long> fileIds, FileDeleteMode mode) {
        Long safeUserId = requiredId(userId, "workspace mutation task missing userId");
        List<Long> safeFileIds = requiredFileIds(fileIds, "delete task missing fileIds");
        FileDeleteMode deleteMode = mode == null ? FileDeleteMode.RECYCLE : mode;
        Map<String, Object> state = baseState("DELETE", safeFileIds, "删除任务已提交");
        state.put("deleteMode", deleteMode.name());
        BackgroundTask task = backgroundTaskService.createQueuedTaskByUserId(
                safeUserId,
                BackgroundTaskType.WORKSPACE_MUTATION,
                state,
                privateState(state),
                correlationId("delete")
        );
        backgroundTaskWorker.wakeLightweightTasks();
        return toView(task);
    }

    private Map<String, Object> baseState(String operation, List<Long> fileIds, String message) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("operation", operation);
        state.put("fileIds", fileIds);
        state.put("processedItems", 0);
        state.put("totalItems", fileIds.size());
        state.put("progressPercent", 0);
        state.put("message", message);
        return state;
    }

    private Map<String, Object> privateState(Map<String, Object> state) {
        Map<String, Object> privateState = new LinkedHashMap<>(state);
        privateState.put("taskType", BackgroundTaskType.WORKSPACE_MUTATION.name());
        return privateState;
    }

    private String correlationId(String operation) {
        return "workspace-mutation:" + operation + ":" + UUID.randomUUID().toString().replace("-", "");
    }

    private Long requiredId(Long value, String message) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return value;
    }

    private List<Long> requiredFileIds(List<Long> fileIds, String message) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        List<Long> result = new ArrayList<>();
        for (Long fileId : fileIds) {
            if (fileId == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "workspace mutation task contains invalid fileId");
            }
            if (!result.contains(fileId)) {
                result.add(fileId);
            }
        }
        return List.copyOf(result);
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return value.trim();
    }

    private WorkspaceMutationTaskView toView(BackgroundTask task) {
        return new WorkspaceMutationTaskView(
                task.getId(),
                task.getType().name(),
                task.getStatus().name(),
                task.getUserId(),
                task.getPublicStateJson(),
                StringUtils.hasText(task.getCorrelationId()) ? task.getCorrelationId() : null,
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }
}
