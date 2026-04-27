package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransferMetricsRecorder {

    private final TransferRuntimeMetricsPort transferRuntimeMetricsPort;
    private final TransactionOperations transactionOperations;

    @Autowired
    TransferMetricsRecorder(TransferRuntimeMetricsPort transferRuntimeMetricsPort,
                            PlatformTransactionManager transactionManager) {
        this.transferRuntimeMetricsPort = transferRuntimeMetricsPort;
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionOperations = transactionTemplate;
    }

    TransferMetricsRecorder(TransferRuntimeMetricsPort transferRuntimeMetricsPort) {
        this.transferRuntimeMetricsPort = transferRuntimeMetricsPort;
        this.transactionOperations = null;
    }

    void recordTransferUsageAfterCommit(long bytes) {
        if (bytes <= 0) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            transferRuntimeMetricsPort.recordTransferUsage(bytes);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (transactionOperations == null) {
                    transferRuntimeMetricsPort.recordTransferUsage(bytes);
                    return;
                }
                transactionOperations.executeWithoutResult(status -> transferRuntimeMetricsPort.recordTransferUsage(bytes));
            }
        });
    }
}
