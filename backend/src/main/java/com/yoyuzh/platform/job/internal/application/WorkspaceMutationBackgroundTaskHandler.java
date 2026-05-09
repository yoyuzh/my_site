package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.api.FileDeleteMode;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceBackgroundMutationApi;
import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkspaceMutationBackgroundTaskHandler implements BackgroundTaskHandler {

    private final WorkspaceBackgroundMutationApi workspaceBackgroundMutationApi;
    private final BackgroundTaskStateManager stateManager;

    public WorkspaceMutationBackgroundTaskHandler(WorkspaceBackgroundMutationApi workspaceBackgroundMutationApi,
                                                  BackgroundTaskStateManager stateManager) {
        this.workspaceBackgroundMutationApi = workspaceBackgroundMutationApi;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.WORKSPACE_MUTATION;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, progress -> {
        });
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Map<String, Object> state = stateManager.mergeJsonObjects(
                task.getPublicStateJson(),
                task.getPrivateStateJson(),
                "workspace mutation task state is invalid"
        );
        String operation = requiredText(state.get("operation"), "workspace mutation task missing operation");
        return switch (operation) {
            case "RENAME" -> rename(task, state);
            case "MOVE" -> move(task, state);
            case "DELETE" -> delete(task, state, progressReporter);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "workspace mutation operation is not supported");
        };
    }

    private BackgroundTaskHandlerResult rename(BackgroundTask task, Map<String, Object> state) {
        Long fileId = requiredLong(state.get("fileId"), "rename task missing fileId");
        String filename = requiredText(state.get("filename"), "rename task missing filename");
        FileMetadataResponse renamedFile = workspaceBackgroundMutationApi.rename(task.getUserId(), fileId, filename);

        Map<String, Object> patch = completedPatch("RENAME", 1, 1, "重命名完成");
        patch.put("fileId", renamedFile.id());
        patch.put("filename", renamedFile.filename());
        patch.put("path", renamedFile.path());
        return new BackgroundTaskHandlerResult(patch);
    }

    private BackgroundTaskHandlerResult move(BackgroundTask task, Map<String, Object> state) {
        List<Long> fileIds = requiredFileIds(state);
        String targetPath = requiredText(state.get("targetPath"), "move task missing targetPath");
        WorkspaceMoveConflictStrategy conflictStrategy = readConflictStrategy(state.get("conflictStrategy"));

        WorkspaceMoveResult result = fileIds.size() == 1
                ? workspaceBackgroundMutationApi.move(task.getUserId(), fileIds.get(0), targetPath, conflictStrategy)
                : workspaceBackgroundMutationApi.batchMove(task.getUserId(), fileIds, targetPath, conflictStrategy);
        if (!"SUCCESS".equals(result.status().name())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, result.message() == null ? "move task failed" : result.message());
        }

        Map<String, Object> patch = completedPatch("MOVE", fileIds.size(), fileIds.size(), "移动完成");
        patch.put("targetPath", targetPath);
        patch.put("moveStatus", result.status().name());
        patch.put("items", result.items());
        return new BackgroundTaskHandlerResult(patch);
    }

    private BackgroundTaskHandlerResult delete(BackgroundTask task,
                                               Map<String, Object> state,
                                               BackgroundTaskProgressReporter progressReporter) {
        List<Long> fileIds = requiredFileIds(state);
        FileDeleteMode deleteMode = readDeleteMode(state.get("deleteMode"));
        int processedItems = 0;
        for (Long fileId : fileIds) {
            workspaceBackgroundMutationApi.delete(task.getUserId(), fileId, deleteMode);
            processedItems += 1;
            progressReporter.report(progressPatch("DELETE", processedItems, fileIds.size(), "删除中"));
        }

        Map<String, Object> patch = completedPatch("DELETE", processedItems, fileIds.size(), "删除完成");
        patch.put("deletedFileIds", fileIds);
        patch.put("deleteMode", deleteMode.name());
        return new BackgroundTaskHandlerResult(patch);
    }

    private Map<String, Object> completedPatch(String operation, int processedItems, int totalItems, String message) {
        Map<String, Object> patch = progressPatch(operation, processedItems, totalItems, message);
        patch.put("progressPercent", 100);
        return patch;
    }

    private Map<String, Object> progressPatch(String operation, int processedItems, int totalItems, String message) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("operation", operation);
        patch.put("processedItems", processedItems);
        patch.put("totalItems", totalItems);
        patch.put("progressPercent", totalItems <= 0 ? 100 : Math.min(100, (int) Math.floor((processedItems * 100.0d) / totalItems)));
        patch.put("message", message);
        return patch;
    }

    private List<Long> requiredFileIds(Map<String, Object> state) {
        Object value = state.get("fileIds");
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "workspace mutation task missing fileIds");
        }
        return values.stream()
                .map(item -> requiredLong(item, "workspace mutation task contains invalid fileId"))
                .distinct()
                .toList();
    }

    private Long requiredLong(Object value, String message) {
        Long result = stateManager.readLong(value);
        if (result == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return result;
    }

    private String requiredText(Object value, String message) {
        String result = stateManager.readText(value);
        if (!StringUtils.hasText(result)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return result;
    }

    private WorkspaceMoveConflictStrategy readConflictStrategy(Object value) {
        String text = stateManager.readText(value);
        return StringUtils.hasText(text) ? WorkspaceMoveConflictStrategy.valueOf(text) : null;
    }

    private FileDeleteMode readDeleteMode(Object value) {
        String text = stateManager.readText(value);
        return StringUtils.hasText(text) ? FileDeleteMode.valueOf(text) : FileDeleteMode.RECYCLE;
    }
}
