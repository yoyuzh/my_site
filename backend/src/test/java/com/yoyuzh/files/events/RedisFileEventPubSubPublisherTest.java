package com.yoyuzh.files.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.config.AppRedisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFileEventPubSubPublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private RedisFileEventPubSubPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RedisFileEventPubSubPublisher(
                stringRedisTemplate,
                objectMapper,
                createRedisProperties(),
                "instance-a"
        );
    }

    @Test
    void shouldPublishEventEnvelopeWithOriginInstanceId() throws Exception {
        when(objectMapper.writeValueAsString(any(FileEventPubSubMessage.class))).thenReturn("{\"ok\":true}");
        FileEvent event = new FileEvent();
        event.setId(10L);
        event.setUserId(7L);
        event.setEventType(FileEventType.RENAMED);
        event.setFileId(11L);
        event.setFromPath("/docs/old.txt");
        event.setToPath("/docs/new.txt");
        event.setClientId("tab-1");
        event.setPayloadJson("{\"action\":\"RENAMED\"}");
        event.setCreatedAt(LocalDateTime.now());

        publisher.publish(event);

        ArgumentCaptor<FileEventPubSubMessage> messageCaptor = ArgumentCaptor.forClass(FileEventPubSubMessage.class);
        verify(objectMapper).writeValueAsString(messageCaptor.capture());
        assertThat(messageCaptor.getValue().originInstanceId()).isEqualTo("instance-a");
        assertThat(messageCaptor.getValue().eventId()).isEqualTo(10L);
        verify(stringRedisTemplate).convertAndSend("yoyuzh:file-events:pubsub", "{\"ok\":true}");
    }

    private AppRedisProperties createRedisProperties() {
        AppRedisProperties properties = new AppRedisProperties();
        properties.setKeyPrefix("yoyuzh");
        properties.getNamespaces().setFileEvents("file-events");
        return properties;
    }
}
