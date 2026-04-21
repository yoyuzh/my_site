package com.yoyuzh.platform.job.internal.application;

import com.yoyuzh.files.workspace.internal.domain.StoredFile;
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

    public void publishAfterCommit(StoredFile storedFile) {
        if (!shouldPublish(storedFile)) {
            return;
        }

        Runnable publishTask = () -> lightweightBrokerGateway.publish(TOPIC, Map.of(
                "userId", storedFile.getUser().getId(),
                "fileId", storedFile.getId(),
                "correlationId", buildCorrelationId(storedFile)
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

    private boolean shouldPublish(StoredFile storedFile) {
        return storedFile != null
                && storedFile.getId() != null
                && storedFile.getUser() != null
                && storedFile.getUser().getId() != null
                && !storedFile.isDirectory()
                && MediaTaskSupport.isMediaLike(storedFile.getFilename(), storedFile.getContentType());
    }

    private String buildCorrelationId(StoredFile storedFile) {
        return "media-meta:auto:file:" + storedFile.getId();
    }
}
