package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.infra.broker.LightweightBrokerGateway;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
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
    private LightweightBrokerGateway lightweightBrokerGateway;

    @Mock
    private BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;

    private MediaMetadataTaskBrokerConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MediaMetadataTaskBrokerConsumer(lightweightBrokerGateway, backgroundTaskLifecycleApi);
    }

    @Test
    void shouldDrainQueuedBrokerMessageIntoAutoMediaMetadataTask() {
        when(lightweightBrokerGateway.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(Map.of(
                        "userId", 7L,
                        "fileId", 11L,
                        "correlationId", "media-meta:auto:file:11"
                )))
                .thenReturn(Optional.empty());
        when(backgroundTaskLifecycleApi.createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11"))
                .thenReturn(Optional.of(new BackgroundTaskView(
                        99L, null, null, 7L, "{}", "media-meta:auto:file:11", null, null, null, null
                )));

        int processed = consumer.drainQueuedMessages(5);

        assertThat(processed).isEqualTo(1);
        verify(backgroundTaskLifecycleApi).createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11");
    }

    @Test
    void shouldRequeuePayloadWhenTaskCreationFails() {
        Map<String, Object> payload = Map.of(
                "userId", 7L,
                "fileId", 11L,
                "correlationId", "media-meta:auto:file:11"
        );
        when(lightweightBrokerGateway.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(payload));
        doThrow(new IllegalStateException("db unavailable"))
                .when(backgroundTaskLifecycleApi)
                .createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11");

        int processed = consumer.drainQueuedMessages(1);

        assertThat(processed).isEqualTo(0);
        verify(lightweightBrokerGateway).requeue(
                MediaMetadataTaskBrokerPublisher.TOPIC,
                Map.of(
                        "userId", 7L,
                        "fileId", 11L,
                        "correlationId", "media-meta:auto:file:11",
                        "brokerRetryCount", 1L
                )
        );
    }

    @Test
    void shouldDropMalformedPayloadWithoutRequeue() {
        when(lightweightBrokerGateway.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(Map.of(
                        "userId", "bad-user-id",
                        "fileId", 11L
                )))
                .thenReturn(Optional.empty());

        int processed = consumer.drainQueuedMessages(2);

        assertThat(processed).isEqualTo(0);
        verify(backgroundTaskLifecycleApi, never()).createQueuedAutoMediaMetadataTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(lightweightBrokerGateway, never()).requeue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldDropPayloadAfterMaxBrokerRetries() {
        Map<String, Object> payload = Map.of(
                "userId", 7L,
                "fileId", 11L,
                "correlationId", "media-meta:auto:file:11",
                "brokerRetryCount", 3
        );
        when(lightweightBrokerGateway.poll(MediaMetadataTaskBrokerPublisher.TOPIC))
                .thenReturn(Optional.of(payload));
        doThrow(new IllegalStateException("db unavailable"))
                .when(backgroundTaskLifecycleApi)
                .createQueuedAutoMediaMetadataTask(7L, 11L, "media-meta:auto:file:11");

        int processed = consumer.drainQueuedMessages(1);

        assertThat(processed).isEqualTo(0);
        verify(lightweightBrokerGateway, never()).requeue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
