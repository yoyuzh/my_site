package com.yoyuzh.ops.admin.internal.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMetricsStartupInitializer {

    private final AdminMetricsService adminMetricsService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        adminMetricsService.initializeState();
    }
}
