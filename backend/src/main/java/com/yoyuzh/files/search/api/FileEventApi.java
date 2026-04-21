package com.yoyuzh.files.search.api;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface FileEventApi {

    SseEmitter openStream(Long userId, String path, String clientId);

    void record(FileEventRecordCommand command);
}
