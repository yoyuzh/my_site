package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.infra.broker.LightweightBrokerGateway;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MediaMetadataTaskBrokerConsumer {

    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int MAX_BROKER_RETRY_COUNT = 3;
    private static final String BROKER_RETRY_COUNT_KEY = "brokerRetryCount";

    private static final Logger log = LoggerFactory.getLogger(MediaMetadataTaskBrokerConsumer.class);

    private final LightweightBrokerGateway lightweightBrokerGateway;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;

    public MediaMetadataTaskBrokerConsumer(LightweightBrokerGateway lightweightBrokerGateway,
                                           BackgroundTaskLifecycleApi backgroundTaskLifecycleApi) {
        this.lightweightBrokerGateway = lightweightBrokerGateway;
        this.backgroundTaskLifecycleApi = backgroundTaskLifecycleApi;
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
            var payload = lightweightBrokerGateway.poll(MediaMetadataTaskBrokerPublisher.TOPIC);
            if (payload.isEmpty()) {
                break;
            }
            try {
                if (handlePayload(payload.get())) {
                    processed += 1;
                }
            } catch (RuntimeException ex) {
                if (shouldRequeue(payload.get())) {
                    lightweightBrokerGateway.requeue(MediaMetadataTaskBrokerPublisher.TOPIC, nextRetryPayload(payload.get()));
                } else {
                    log.error("Dropping poison broker payload after {} retries: {}", MAX_BROKER_RETRY_COUNT, payload.get(), ex);
                }
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
            log.warn("Dropping malformed broker payload: {}", payload);
            return false;
        }
        backgroundTaskLifecycleApi.createQueuedAutoMediaMetadataTask(userId, fileId, correlationId);
        return true;
    }

    private boolean shouldRequeue(Map<String, Object> payload) {
        Long retryCount = readLong(payload == null ? null : payload.get(BROKER_RETRY_COUNT_KEY));
        return retryCount == null || retryCount < MAX_BROKER_RETRY_COUNT;
    }

    private Map<String, Object> nextRetryPayload(Map<String, Object> payload) {
        Map<String, Object> nextPayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        long retryCount = readLong(nextPayload.get(BROKER_RETRY_COUNT_KEY)) == null
                ? 0L
                : readLong(nextPayload.get(BROKER_RETRY_COUNT_KEY));
        nextPayload.put(BROKER_RETRY_COUNT_KEY, retryCount + 1L);
        return nextPayload;
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
