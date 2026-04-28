package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;
import java.util.List;

public record FileDetailResponse(
        Long id,
        String filename,
        String path,
        long size,
        String contentType,
        boolean directory,
        boolean favorite,
        boolean shared,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String customEmoji,
        String folderColor,
        List<WorkspaceTagResponse> tags
) {
    public FileDetailResponse withTags(List<WorkspaceTagResponse> updatedTags) {
        return new FileDetailResponse(
                id,
                filename,
                path,
                size,
                contentType,
                directory,
                favorite,
                shared,
                createdAt,
                updatedAt,
                customEmoji,
                folderColor,
                updatedTags
        );
    }
}
