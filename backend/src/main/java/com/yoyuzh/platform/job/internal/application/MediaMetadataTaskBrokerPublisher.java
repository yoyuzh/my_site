package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.infra.broker.LightweightBrokerGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Component
public class MediaMetadataTaskBrokerPublisher {

    public static final String TOPIC = "media-metadata-trigger";

    private final LightweightBrokerGateway lightweightBrokerGateway;

    public MediaMetadataTaskBrokerPublisher(LightweightBrokerGateway lightweightBrokerGateway) {
        this.lightweightBrokerGateway = lightweightBrokerGateway;
    }

    public void publishAfterCommit(WorkspaceFileSnapshot file) {
        if (!shouldPublish(file)) {
            return;
        }

        Runnable publishTask = () -> lightweightBrokerGateway.publish(TOPIC, Map.of(
                "userId", file.userId(),
                "fileId", file.id(),
                "correlationId", buildCorrelationId(file)
        ));

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }

        publishTask.run();
    }

    private boolean shouldPublish(WorkspaceFileSnapshot file) {
        return file != null
                && file.id() != null
                && file.userId() != null
                && !file.directory()
                && MediaTaskSupport.isMediaLike(file.filename(), file.contentType());
    }

    private String buildCorrelationId(WorkspaceFileSnapshot file) {
        return "media-meta:auto:file:" + file.id();
    }
}
