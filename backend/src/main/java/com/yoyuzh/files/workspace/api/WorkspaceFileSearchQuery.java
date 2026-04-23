package com.yoyuzh.files.workspace.api;

import java.time.LocalDateTime;

public record WorkspaceFileSearchQuery(
        String name,
        Boolean directory,
        Long sizeGte,
        Long sizeLte,
        LocalDateTime createdGte,
        LocalDateTime createdLte,
        LocalDateTime updatedGte,
        LocalDateTime updatedLte,
        int page,
        int size
) {
}
