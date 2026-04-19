package com.yoyuzh.infra.broker;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryLightweightBrokerGateway implements LightweightBrokerGateway {

    private final ConcurrentHashMap<String, Deque<Map<String, Object>>> queues = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, Map<String, Object> payload) {
        queues.computeIfAbsent(topic, ignored -> new ConcurrentLinkedDeque<>())
                .offerLast(copyPayload(payload));
    }

    @Override
    public Optional<Map<String, Object>> poll(String topic) {
        Deque<Map<String, Object>> queue = queues.get(topic);
        if (queue == null) {
            return Optional.empty();
        }
        Map<String, Object> payload = queue.pollFirst();
        return payload == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(payload));
    }

    @Override
    public void requeue(String topic, Map<String, Object> payload) {
        queues.computeIfAbsent(topic, ignored -> new ConcurrentLinkedDeque<>())
                .offerFirst(copyPayload(payload));
    }

    private Map<String, Object> copyPayload(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }
}
