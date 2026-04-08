package com.yoyuzh.files;

import java.time.LocalDateTime;

public record FileSearchQuery(
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
