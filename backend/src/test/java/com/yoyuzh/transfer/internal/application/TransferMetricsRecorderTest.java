package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TransferMetricsRecorderTest {

    @Test
    void shouldDelayTransferUsageRecordingUntilAfterCommit() {
        TransferRuntimeMetricsPort metricsPort = mock(TransferRuntimeMetricsPort.class);
        TransferMetricsRecorder recorder = new TransferMetricsRecorder(metricsPort);

        TransactionSynchronizationManager.initSynchronization();
        try {
            recorder.recordTransferUsageAfterCommit(128L);

            verify(metricsPort, never()).recordTransferUsage(128L);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(metricsPort).recordTransferUsage(128L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRecordTransferUsageImmediatelyWhenNoTransactionSynchronizationIsActive() {
        TransferRuntimeMetricsPort metricsPort = mock(TransferRuntimeMetricsPort.class);
        TransferMetricsRecorder recorder = new TransferMetricsRecorder(metricsPort);

        recorder.recordTransferUsageAfterCommit(128L);

        verify(metricsPort).recordTransferUsage(128L);
    }
}
