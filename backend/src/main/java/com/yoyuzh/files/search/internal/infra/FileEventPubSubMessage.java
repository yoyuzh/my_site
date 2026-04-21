package com.yoyuzh.files.search.internal.infra;

import com.yoyuzh.files.search.api.FileEventType;
import java.time.LocalDateTime;

record FileEventPubSubMessage(
        String originInstanceId,
        Long eventId,
        Long userId,
        FileEventType eventType,
        Long fileId,
        String fromPath,
        String toPath,
        String clientId,
        String payloadJson,
        LocalDateTime createdAt
) {
}
