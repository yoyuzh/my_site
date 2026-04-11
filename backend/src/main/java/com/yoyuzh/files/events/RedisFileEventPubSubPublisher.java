package com.yoyuzh.files.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.config.AppRedisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisFileEventPubSubPublisher implements FileEventCrossInstancePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AppRedisProperties redisProperties;
    private final String instanceId;

    @Autowired
    public RedisFileEventPubSubPublisher(StringRedisTemplate stringRedisTemplate,
                                         ObjectMapper objectMapper,
                                         AppRedisProperties redisProperties,
                                         FileEventInstanceIdentity instanceIdentity) {
        this(stringRedisTemplate, objectMapper, redisProperties, instanceIdentity.getInstanceId());
    }

    RedisFileEventPubSubPublisher(StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper,
                                  AppRedisProperties redisProperties,
                                  String instanceId) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
        this.instanceId = instanceId;
    }

    @Override
    public void publish(FileEvent event) {
        try {
            stringRedisTemplate.convertAndSend(
                    buildTopic(),
                    objectMapper.writeValueAsString(toMessage(event))
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize file event pub/sub payload", ex);
        }
    }

    String buildTopic() {
        return redisProperties.getKeyPrefix()
                + ":"
                + redisProperties.getNamespaces().getFileEvents()
                + ":pubsub";
    }

    private FileEventPubSubMessage toMessage(FileEvent event) {
        return new FileEventPubSubMessage(
                instanceId,
                event.getId(),
                event.getUserId(),
                event.getEventType(),
                event.getFileId(),
                event.getFromPath(),
                event.getToPath(),
                event.getClientId(),
                event.getPayloadJson(),
                event.getCreatedAt()
        );
    }
}
