package com.yoyuzh.files;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileEventService {
    private static final String CLIENT_ID_HEADER = "X-Yoyuzh-Client-Id";
    private static final String READY_EVENT_NAME = "READY";

    private final FileEventRepository fileEventRepository;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public FileEventService(FileEventRepository fileEventRepository, ObjectMapper objectMapper) {
        this.fileEventRepository = fileEventRepository;
        this.objectMapper = objectMapper;
    }

    public SseEmitter openStream(User user, String path, String clientId) {
        String normalizedPath = normalizePath(path);
        SseEmitter emitter = createEmitter();
        Subscription subscription = new Subscription(emitter, normalizedPath, normalizeClientId(clientId));
        subscriptions.computeIfAbsent(user.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(subscription);
        emitter.onCompletion(() -> removeSubscription(user.getId(), subscription));
        emitter.onTimeout(() -> removeSubscription(user.getId(), subscription));
        emitter.onError(ex -> removeSubscription(user.getId(), subscription));

        try {
            emitter.send(SseEmitter.event()
                    .name(READY_EVENT_NAME)
                    .data(createReadyPayload(normalizedPath, subscription.clientId)));
        } catch (IOException ex) {
            removeSubscription(user.getId(), subscription);
            throw new IllegalStateException("Failed to initialize file event stream", ex);
        }
        return emitter;
    }

    public FileEvent record(User user,
                            FileEventType eventType,
                            Long fileId,
                            String fromPath,
                            String toPath,
                            String clientId,
                            Map<String, Object> payload) {
        FileEvent event = new FileEvent();
        event.setUserId(user.getId());
        event.setEventType(eventType);
        event.setFileId(fileId);
        event.setFromPath(fromPath);
        event.setToPath(toPath);
        event.setClientId(resolveClientId(clientId));
        event.setPayloadJson(toJson(payload));
        fileEventRepository.save(event);
        broadcast(event);
        return event;
    }

    public FileEvent record(User user,
                            FileEventType eventType,
                            Long fileId,
                            String fromPath,
                            String toPath,
                            Map<String, Object> payload) {
        return record(user, eventType, fileId, fromPath, toPath, null, payload);
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter();
    }

    private void broadcast(FileEvent event) {
        Runnable broadcastTask = () -> {
            Set<Subscription> userSubscriptions = subscriptions.get(event.getUserId());
            if (userSubscriptions == null || userSubscriptions.isEmpty()) {
                return;
            }

            for (Subscription subscription : userSubscriptions.toArray(new Subscription[0])) {
                if (!subscription.matches(event)) {
                    continue;
                }
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("eventType", event.getEventType().name());
                    payload.put("fileId", event.getFileId());
                    payload.put("fromPath", event.getFromPath());
                    payload.put("toPath", event.getToPath());
                    payload.put("clientId", event.getClientId());
                    payload.put("createdAt", event.getCreatedAt());
                    payload.put("payload", event.getPayloadJson());
                    subscription.emitter.send(SseEmitter.event()
                            .name(event.getEventType().name())
                            .data(payload));
                } catch (IOException | IllegalStateException ex) {
                    removeSubscription(event.getUserId(), subscription);
                }
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastTask.run();
                }
            });
            return;
        }

        broadcastTask.run();
    }

    private void removeSubscription(Long userId, Subscription subscription) {
        Set<Subscription> userSubscriptions = subscriptions.get(userId);
        if (userSubscriptions == null) {
            return;
        }
        userSubscriptions.remove(subscription);
        if (userSubscriptions.isEmpty()) {
            subscriptions.remove(userId, userSubscriptions);
        }
    }

    private String toJson(Map<String, Object> payload) {
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

    private String resolveClientId(String explicitClientId) {
        if (StringUtils.hasText(explicitClientId)) {
            return normalizeClientId(explicitClientId);
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return normalizeClientId(request.getHeader(CLIENT_ID_HEADER));
    }

    private String normalizeClientId(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        String cleaned = clientId.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }

        String cleaned = path.trim().replace("\\", "/");
        while (cleaned.contains("//")) {
            cleaned = cleaned.replace("//", "/");
        }
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }
        if (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private boolean isPathMatch(String filterPath, String eventPath) {
        if (!StringUtils.hasText(filterPath) || "/".equals(filterPath)) {
            return true;
        }
        if (!StringUtils.hasText(eventPath)) {
            return false;
        }
        return Objects.equals(filterPath, eventPath) || eventPath.startsWith(filterPath + "/");
    }

    private Map<String, Object> createReadyPayload(String path, String clientId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", READY_EVENT_NAME);
        payload.put("path", path);
        payload.put("clientId", clientId);
        payload.put("createdAt", LocalDateTime.now());
        return payload;
    }

    private final class Subscription {
        private final SseEmitter emitter;
        private final String path;
        private final String clientId;

        private Subscription(SseEmitter emitter, String path, String clientId) {
            this.emitter = emitter;
            this.path = path;
            this.clientId = clientId;
        }

        private boolean matches(FileEvent event) {
            boolean pathMatches;
            if (event.getFromPath() != null && event.getToPath() != null) {
                pathMatches = FileEventService.this.isPathMatch(path, event.getFromPath())
                        || FileEventService.this.isPathMatch(path, event.getToPath());
            } else {
                String eventPath = event.getToPath() != null ? event.getToPath() : event.getFromPath();
                pathMatches = FileEventService.this.isPathMatch(path, eventPath);
            }

            if (!pathMatches) {
                return false;
            }
            return clientId == null || event.getClientId() == null || !clientId.equals(event.getClientId());
        }
    }
}
