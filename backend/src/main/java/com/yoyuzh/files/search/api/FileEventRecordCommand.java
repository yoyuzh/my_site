package com.yoyuzh.files.search.api;

import java.util.Map;

public record FileEventRecordCommand(
        Long userId,
        FileEventType eventType,
        Long fileId,
        String fromPath,
        String toPath,
        String clientId,
        Map<String, Object> payload
) {
}
