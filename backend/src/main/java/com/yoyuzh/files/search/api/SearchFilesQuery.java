package com.yoyuzh.files.search.api;

import java.time.LocalDateTime;

public record SearchFilesQuery(
        String name,
        String category,
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
