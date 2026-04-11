package com.yoyuzh.files.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.config.AppRedisProperties;
import org.springframework.data.redis.connection.DefaultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFileEventPubSubListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FileEventService fileEventService;

    private RedisFileEventPubSubListener listener;

    @BeforeEach
    void setUp() {
        listener = new RedisFileEventPubSubListener(
                objectMapper,
                createRedisProperties(),
                fileEventService,
                "instance-a"
        );
    }

    @Test
    void shouldIgnoreEventPublishedBySameInstance() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(FileEventPubSubMessage.class)))
                .thenReturn(new FileEventPubSubMessage(
                        "instance-a",
                        10L,
                        7L,
                        FileEventType.RENAMED,
                        11L,
                        "/docs/old.txt",
                        "/docs/new.txt",
                        "tab-1",
                        "{\"action\":\"RENAMED\"}",
                        LocalDateTime.now()
                ));

        listener.onMessage(createMessage("{\"ok\":true}"), null);

        verify(fileEventService, never()).broadcastReplicatedEvent(any(FileEvent.class));
    }

    @Test
    void shouldForwardRemoteEventToLocalSubscribers() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(FileEventPubSubMessage.class)))
                .thenReturn(new FileEventPubSubMessage(
                        "instance-b",
                        10L,
                        7L,
                        FileEventType.RENAMED,
                        11L,
                        "/docs/old.txt",
                        "/docs/new.txt",
                        "tab-1",
                        "{\"action\":\"RENAMED\"}",
                        LocalDateTime.now()
                ));

        listener.onMessage(createMessage("{\"ok\":true}"), null);

        verify(fileEventService).broadcastReplicatedEvent(any(FileEvent.class));
    }

    @Test
    void shouldDropMalformedPayloadWithoutBreakingListener() throws Exception {
        doThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad json"))
                .when(objectMapper)
                .readValue(any(String.class), eq(FileEventPubSubMessage.class));

        listener.onMessage(createMessage("{bad-json"), null);

        verify(fileEventService, never()).broadcastReplicatedEvent(any(FileEvent.class));
    }

    private Message createMessage(String payload) {
        return new DefaultMessage(payload.getBytes(StandardCharsets.UTF_8), "file-events".getBytes(StandardCharsets.UTF_8));
    }

    private AppRedisProperties createRedisProperties() {
        AppRedisProperties properties = new AppRedisProperties();
        properties.setKeyPrefix("yoyuzh");
        properties.getNamespaces().setFileEvents("file-events");
        return properties;
    }
}
