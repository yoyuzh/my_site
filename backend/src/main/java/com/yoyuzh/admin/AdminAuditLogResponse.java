package com.yoyuzh.admin;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long actorUserId,
        String actorUsername,
        String actorAuthorities,
        String actionType,
        String targetType,
        Long targetId,
        String summary,
        String detailsJson,
        LocalDateTime createdAt
) {
}
