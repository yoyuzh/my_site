package com.yoyuzh.files.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FileEventPayloadCodec {

    private final ObjectMapper objectMapper;

    public FileEventPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        if (!safePayload.containsKey("createdAt")) {
            safePayload.put("createdAt", LocalDateTime.now());
        }
        try {
            return objectMapper.writeValueAsString(safePayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize file event payload", ex);
        }
    }

    public Map<String, Object> createReadyPayload(String path, String clientId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "READY");
        payload.put("path", path);
        payload.put("clientId", clientId);
        payload.put("createdAt", LocalDateTime.now());
        return payload;
    }

    public Map<String, Object> createEmitterPayload(FileEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.getEventType().name());
        payload.put("fileId", event.getFileId());
        payload.put("fromPath", event.getFromPath());
        payload.put("toPath", event.getToPath());
        payload.put("clientId", event.getClientId());
        payload.put("createdAt", event.getCreatedAt());
        payload.put("payload", event.getPayloadJson());
        return payload;
    }
}
