package com.yoyuzh.files.events;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FileEventDispatcher {

    private static final String READY_EVENT_NAME = "READY";

    private final FileEventPayloadCodec payloadCodec;
    private final ConcurrentHashMap<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public FileEventDispatcher(FileEventPayloadCodec payloadCodec) {
        this.payloadCodec = payloadCodec;
    }

    public SseEmitter openStream(Long userId, String path, String clientId) {
        String normalizedPath = normalizePath(path);
        String normalizedClientId = normalizeClientId(clientId);
        SseEmitter emitter = createEmitter();
        Subscription subscription = new Subscription(emitter, normalizedPath, normalizedClientId);
        subscriptions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(subscription);
        emitter.onCompletion(() -> removeSubscription(userId, subscription));
        emitter.onTimeout(() -> removeSubscription(userId, subscription));
        emitter.onError(ex -> removeSubscription(userId, subscription));

        try {
            emitter.send(SseEmitter.event()
                    .name(READY_EVENT_NAME)
                    .data(payloadCodec.createReadyPayload(normalizedPath, normalizedClientId)));
        } catch (IOException ex) {
            removeSubscription(userId, subscription);
            throw new IllegalStateException("Failed to initialize file event stream", ex);
        }
        return emitter;
    }

    public void broadcast(FileEvent event) {
        Set<Subscription> userSubscriptions = subscriptions.get(event.getUserId());
        if (userSubscriptions == null || userSubscriptions.isEmpty()) {
            return;
        }

        for (Subscription subscription : userSubscriptions.toArray(new Subscription[0])) {
            if (!subscription.matches(event)) {
                continue;
            }
            try {
                subscription.emitter.send(SseEmitter.event()
                        .name(event.getEventType().name())
                        .data(payloadCodec.createEmitterPayload(event)));
            } catch (IOException | IllegalStateException ex) {
                removeSubscription(event.getUserId(), subscription);
            }
        }
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter();
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
                pathMatches = isPathMatch(path, event.getFromPath()) || isPathMatch(path, event.getToPath());
            } else {
                String eventPath = event.getToPath() != null ? event.getToPath() : event.getFromPath();
                pathMatches = isPathMatch(path, eventPath);
            }
            if (!pathMatches) {
                return false;
            }
            return clientId == null || event.getClientId() == null || !clientId.equals(event.getClientId());
        }
    }
}
