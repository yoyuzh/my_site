package com.yoyuzh.files.tasks;

import com.yoyuzh.common.broker.LightweightBrokerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class MediaMetadataTaskBrokerConsumer {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private final LightweightBrokerService lightweightBrokerService;
    private final BackgroundTaskCommandService backgroundTaskCommandService;

    public MediaMetadataTaskBrokerConsumer(LightweightBrokerService lightweightBrokerService,
                                           BackgroundTaskCommandService backgroundTaskCommandService) {
        this.lightweightBrokerService = lightweightBrokerService;
        this.backgroundTaskCommandService = backgroundTaskCommandService;
    }

    @Scheduled(
            fixedDelayString = "${app.redis.broker.media-meta.fixed-delay-ms:3000}",
            initialDelayString = "${app.redis.broker.media-meta.initial-delay-ms:15000}"
    )
    public void runScheduledBatch() {
        drainQueuedMessages(DEFAULT_BATCH_SIZE);
    }

    public int drainQueuedMessages(int maxMessages) {
        int safeLimit = Math.max(0, maxMessages);
        int processed = 0;
        for (int i = 0; i < safeLimit; i++) {
            var payload = lightweightBrokerService.poll(MediaMetadataTaskBrokerPublisher.TOPIC);
            if (payload.isEmpty()) {
                break;
            }
            try {
                if (handlePayload(payload.get())) {
                    processed += 1;
                }
            } catch (RuntimeException ex) {
                lightweightBrokerService.requeue(MediaMetadataTaskBrokerPublisher.TOPIC, payload.get());
                break;
            }
        }
        return processed;
    }

    private boolean handlePayload(Map<String, Object> payload) {
        Long userId = readLong(payload.get("userId"));
        Long fileId = readLong(payload.get("fileId"));
        String correlationId = readString(payload.get("correlationId"));
        if (userId == null || fileId == null) {
            return false;
        }
        backgroundTaskCommandService.createQueuedAutoMediaMetadataTask(userId, fileId, correlationId);
        return true;
    }

    private Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String readString(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }
}
