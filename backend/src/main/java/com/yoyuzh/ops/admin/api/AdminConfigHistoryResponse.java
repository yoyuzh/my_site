package com.yoyuzh.ops.admin.api;

import java.time.LocalDateTime;

public record AdminConfigHistoryResponse(
        Long id,
        String key,
        Object beforeValue,
        Object afterValue,
        long version,
        String reason,
        Long actorUserId,
        String actorUsername,
        LocalDateTime createdAt
) {
}
