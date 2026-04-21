package com.yoyuzh.files.search.internal.application;

import com.yoyuzh.files.search.api.FileEventRecordCommand;
import com.yoyuzh.files.search.api.FileEventType;
import com.yoyuzh.files.search.internal.domain.FileEvent;
import com.yoyuzh.files.search.internal.infra.FileEventCrossInstancePublisher;
import com.yoyuzh.files.search.internal.infra.FileEventPayloadCodec;
import com.yoyuzh.files.search.internal.infra.FileEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeFileEventApiTest {

    @Mock
    private FileEventRepository fileEventRepository;

    @Mock
    private FileEventPayloadCodec payloadCodec;

    @Mock
    private FileEventDispatcher fileEventDispatcher;

    @Mock
    private FileEventCrossInstancePublisher fileEventCrossInstancePublisher;

    private RuntimeFileEventApi fileEventApi;

    @BeforeEach
    void setUp() {
        fileEventApi = new RuntimeFileEventApi(
                fileEventRepository,
                payloadCodec,
                fileEventDispatcher,
                fileEventCrossInstancePublisher
        );
    }

    @Test
    void shouldPublishRecordedEventToCrossInstanceChannel() throws Exception {
        when(payloadCodec.toJson(any(Map.class))).thenReturn("{\"action\":\"RENAMED\"}");
        when(fileEventRepository.save(any(FileEvent.class))).thenAnswer(invocation -> {
            FileEvent event = invocation.getArgument(0);
            event.setId(10L);
            event.setCreatedAt(LocalDateTime.now());
            return event;
        });
        fileEventApi.record(new FileEventRecordCommand(
                7L,
                FileEventType.RENAMED,
                10L,
                "/docs/old.txt",
                "/docs/new.txt",
                "tab-1",
                Map.of("action", "RENAMED")
        ));

        verify(fileEventDispatcher).broadcast(any(FileEvent.class));
        verify(fileEventCrossInstancePublisher).publish(any(FileEvent.class));
    }
}
