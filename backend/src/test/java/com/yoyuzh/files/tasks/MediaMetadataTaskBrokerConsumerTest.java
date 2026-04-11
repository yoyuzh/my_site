package com.yoyuzh.files.tasks;

import com.yoyuzh.common.broker.LightweightBrokerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaMetadataTaskBrokerConsumerTest {

    @Mock
    private LightweightBrokerService lightweightBrokerService;

    @Mock
    private BackgroundTaskCommandService backgroundTaskCommandService;

    private MediaMetadataTaskBrokerConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MediaMetadataTaskBrokerConsumer(lightweightBrokerService, backgroundTaskCommandService);
    }

    @Test
    void shouldDrainQueuedBrokerMessageIntoAutoMediaMetadataTask() {
        when(lightweightBrokerService.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(Map.of(
                        "userId", 7L,
                        "fileId", 11L,
                        "correlationId", "media-meta:auto:file:11"
                )))
                .thenReturn(Optional.empty());
        when(backgroundTaskCommandService.createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11"))
                .thenReturn(Optional.of(new BackgroundTask()));

        int processed = consumer.drainQueuedMessages(5);

        assertThat(processed).isEqualTo(1);
        verify(backgroundTaskCommandService).createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11");
    }

    @Test
    void shouldRequeuePayloadWhenTaskCreationFails() {
        Map<String, Object> payload = Map.of(
                "userId", 7L,
                "fileId", 11L,
                "correlationId", "media-meta:auto:file:11"
        );
        when(lightweightBrokerService.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(payload));
        doThrow(new IllegalStateException("db unavailable"))
                .when(backgroundTaskCommandService)
                .createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11");

        int processed = consumer.drainQueuedMessages(1);

        assertThat(processed).isEqualTo(0);
        verify(lightweightBrokerService).requeue(MediaMetadataTaskBrokerPublisher.TOPIC, payload);
    }

    @Test
    void shouldDropMalformedPayloadWithoutRequeue() {
        when(lightweightBrokerService.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(Map.of(
                        "userId", "bad-user-id",
                        "fileId", 11L
                )))
                .thenReturn(Optional.empty());

        int processed = consumer.drainQueuedMessages(2);

        assertThat(processed).isEqualTo(0);
        verify(backgroundTaskCommandService, never()).createQueuedAutoMediaMetadataTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(lightweightBrokerService, never()).requeue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
