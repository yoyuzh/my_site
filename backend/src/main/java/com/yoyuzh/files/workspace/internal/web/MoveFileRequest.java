package com.yoyuzh.files.workspace.internal.web;

import com.yoyuzh.files.workspace.api.WorkspaceMoveConflictStrategy;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.util.StringUtils;

public record MoveFileRequest(
        String targetPath,
        String path,
        WorkspaceMoveConflictStrategy conflictStrategy
) {

    @AssertTrue(message = "目标路径不能为空")
    public boolean hasTargetPath() {
        return StringUtils.hasText(targetPath()) || StringUtils.hasText(path());
    }

    public String resolvedTargetPath() {
        return StringUtils.hasText(targetPath()) ? targetPath() : path();
    }
}
