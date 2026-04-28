package com.yoyuzh.files.workspace.api;

import java.util.List;

public record WorkspaceMoveResult(
        WorkspaceMoveOutcomeStatus status,
        List<WorkspaceMoveItemResult> items,
        List<WorkspaceMoveItemResult> conflicts,
        String message
) {
    public static WorkspaceMoveResult success(List<WorkspaceMoveItemResult> items) {
        return new WorkspaceMoveResult(WorkspaceMoveOutcomeStatus.SUCCESS, items, List.of(), null);
    }

    public static WorkspaceMoveResult conflict(List<WorkspaceMoveItemResult> conflicts) {
        return new WorkspaceMoveResult(WorkspaceMoveOutcomeStatus.CONFLICT, List.of(), conflicts, "目标目录存在同名项目");
    }

    public static WorkspaceMoveResult invalidTarget(String message, List<WorkspaceMoveItemResult> items) {
        return new WorkspaceMoveResult(WorkspaceMoveOutcomeStatus.INVALID_TARGET, items, List.of(), message);
    }
}
