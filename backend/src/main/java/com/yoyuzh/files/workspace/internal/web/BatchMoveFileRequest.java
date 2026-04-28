package com.yoyuzh.files.workspace.internal.web;

import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchMoveFileRequest(
        @NotEmpty(message = "待移动项目不能为空")
        List<Long> fileIds,
        @NotBlank(message = "目标路径不能为空")
        String targetPath,
        WorkspaceMoveConflictStrategy conflictStrategy
) {
}
