package com.yoyuzh.files.workspace.internal.web;

public record UpdateWorkspaceAppearanceRequest(
        String customEmoji,
        String folderColor
) {
}
