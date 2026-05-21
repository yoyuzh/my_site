package com.yoyuzh.files.workspace.internal.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.util.function.Supplier;

@Component
public class WorkspaceRequestProbe {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRequestProbe.class);
    private static final ThreadLocal<ProbeSession> CURRENT = new ThreadLocal<>();
    private static final WorkspaceRequestProbe DISABLED = new WorkspaceRequestProbe(false, true);

    private final boolean enabled;

    @Autowired
    public WorkspaceRequestProbe(@Value("${app.probe.files.enabled:false}") boolean enabled) {
        this(enabled, false);
    }

    private WorkspaceRequestProbe(boolean enabled, boolean ignored) {
        this.enabled = enabled;
    }

    public static WorkspaceRequestProbe disabled() {
        return DISABLED;
    }

    public <T> T trace(String action, Map<String, ?> metadata, Supplier<T> supplier) {
        if (!enabled || CURRENT.get() != null) {
            return supplier.get();
        }
        ProbeSession session = new ProbeSession(action, buildMetadata(metadata));
        CURRENT.set(session);
        long startedAt = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            long totalNanos = System.nanoTime() - startedAt;
            CURRENT.remove();
            log.info(
                    "workspace-probe action={} traceId={} totalMs={} metadata={} stages={}",
                    session.action(),
                    session.traceId(),
                    formatMillis(totalNanos),
                    session.metadata(),
                    formatStages(session.stages())
            );
        }
    }

    public void trace(String action, Map<String, ?> metadata, Runnable runnable) {
        trace(action, metadata, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T measure(String stage, Supplier<T> supplier) {
        if (!enabled) {
            return supplier.get();
        }
        ProbeSession session = CURRENT.get();
        if (session == null) {
            return supplier.get();
        }
        long startedAt = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            session.stages().add(new StageTiming(stage, System.nanoTime() - startedAt));
        }
    }

    public void measure(String stage, Runnable runnable) {
        measure(stage, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T measureIo(String stage, IoSupplier<T> supplier) throws IOException {
        if (!enabled) {
            return supplier.get();
        }
        ProbeSession session = CURRENT.get();
        if (session == null) {
            return supplier.get();
        }
        long startedAt = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            session.stages().add(new StageTiming(stage, System.nanoTime() - startedAt));
        }
    }

    public void putMetadata(String key, Object value) {
        if (!enabled || key == null || key.isBlank() || value == null) {
            return;
        }
        ProbeSession session = CURRENT.get();
        if (session == null) {
            return;
        }
        session.metadata().putIfAbsent(key, value);
    }

    private Map<String, Object> buildMetadata(Map<String, ?> metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return values;
        }
        metadata.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                values.put(key, value);
            }
        });
        return values;
    }

    private String formatStages(List<StageTiming> stages) {
        if (stages.isEmpty()) {
            return "[]";
        }
        List<String> values = new ArrayList<>(stages.size());
        for (StageTiming stage : stages) {
            values.add(stage.name() + "=" + formatMillis(stage.durationNanos()) + "ms");
        }
        return values.toString();
    }

    private String formatMillis(long durationNanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0d);
    }

    private record ProbeSession(String action,
                                String traceId,
                                Map<String, Object> metadata,
                                List<StageTiming> stages) {
        private ProbeSession(String action, Map<String, Object> metadata) {
            this(action, UUID.randomUUID().toString().substring(0, 8), metadata, new ArrayList<>());
        }
    }

    private record StageTiming(String name, long durationNanos) {
    }

    @FunctionalInterface
    public interface IoSupplier<T> {
        T get() throws IOException;
    }
}
