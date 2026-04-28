package com.yoyuzh.files.workspace.api;

public record WorkspaceMoveItemResult(
        Long fileId,
        String filename,
        String fromPath,
        String toPath,
        boolean renamed,
        boolean skipped,
        String customEmoji,
        String folderColor
) {
}
