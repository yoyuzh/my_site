package com.yoyuzh.files.workspace.api;

public record FileViewerTemplate(
        String viewerId,
        String extension,
        String displayName,
        String filename,
        String content,
        String contentType
) {
}
