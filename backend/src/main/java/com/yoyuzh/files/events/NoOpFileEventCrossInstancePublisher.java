package com.yoyuzh.files.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpFileEventCrossInstancePublisher implements FileEventCrossInstancePublisher {

    @Override
    public void publish(FileEvent event) {
        // Redis disabled: keep single-instance in-memory broadcast behavior.
    }
}
