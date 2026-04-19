package com.yoyuzh.infra.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.infra.cache.AppRedisProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisLightweightBrokerGateway implements LightweightBrokerGateway {

    private static final Logger log = LoggerFactory.getLogger(RedisLightweightBrokerGateway.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AppRedisProperties redisProperties;

    public RedisLightweightBrokerGateway(StringRedisTemplate stringRedisTemplate,
                                         ObjectMapper objectMapper,
                                         AppRedisProperties redisProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
    }

    @Override
    public void publish(String topic, Map<String, Object> payload) {
        stringRedisTemplate.opsForList().rightPush(buildQueueKey(topic), toJson(payload));
    }

    @Override
    public Optional<Map<String, Object>> poll(String topic) {
        String queueKey = buildQueueKey(topic);
        while (true) {
            String payload = stringRedisTemplate.opsForList().leftPop(queueKey);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }
            try {
                return Optional.of(parsePayload(payload));
            } catch (IllegalStateException ex) {
                log.warn("Dropping malformed broker payload for topic {}", topic, ex);
            }
        }
    }

    @Override
    public void requeue(String topic, Map<String, Object> payload) {
        stringRedisTemplate.opsForList().leftPush(buildQueueKey(topic), toJson(payload));
    }

    private String buildQueueKey(String topic) {
        return redisProperties.getKeyPrefix()
                + ":"
                + redisProperties.getNamespaces().getBroker()
                + ":"
                + topic
                + ":queue";
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize broker payload", ex);
        }
    }

    private Map<String, Object> parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse broker payload", ex);
        }
    }
}
