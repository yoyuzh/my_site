package com.yoyuzh.files.search.internal.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.search.internal.application.RuntimeFileEventApi;
import com.yoyuzh.files.search.internal.domain.FileEvent;
import com.yoyuzh.infra.cache.AppRedisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisFileEventPubSubListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final AppRedisProperties redisProperties;
    private final RuntimeFileEventApi fileEventApi;
    private final String instanceId;

    @Autowired
    public RedisFileEventPubSubListener(ObjectMapper objectMapper,
                                        AppRedisProperties redisProperties,
                                        RuntimeFileEventApi fileEventApi,
                                        FileEventInstanceIdentity instanceIdentity) {
        this(objectMapper, redisProperties, fileEventApi, instanceIdentity.getInstanceId());
    }

    RedisFileEventPubSubListener(ObjectMapper objectMapper,
                                 AppRedisProperties redisProperties,
                                 RuntimeFileEventApi fileEventApi,
                                 String instanceId) {
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
        this.fileEventApi = fileEventApi;
        this.instanceId = instanceId;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!StringUtils.hasText(payload)) {
            return;
        }
        FileEventPubSubMessage pubSubMessage;
        try {
            pubSubMessage = parsePayload(payload);
        } catch (IllegalStateException ex) {
            return;
        }
        if (instanceId.equals(pubSubMessage.originInstanceId())) {
            return;
        }
        fileEventApi.broadcastReplicatedEvent(toEvent(pubSubMessage));
    }

    String buildTopic() {
        return redisProperties.getKeyPrefix()
                + ":"
                + redisProperties.getNamespaces().getFileEvents()
                + ":pubsub";
    }

    private FileEventPubSubMessage parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, FileEventPubSubMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse file event pub/sub payload", ex);
        }
    }

    private FileEvent toEvent(FileEventPubSubMessage message) {
        FileEvent event = new FileEvent();
        event.setId(message.eventId());
        event.setUserId(message.userId());
        event.setEventType(message.eventType());
        event.setFileId(message.fileId());
        event.setFromPath(message.fromPath());
        event.setToPath(message.toPath());
        event.setClientId(message.clientId());
        event.setPayloadJson(message.payloadJson());
        event.setCreatedAt(message.createdAt());
        return event;
    }
}
