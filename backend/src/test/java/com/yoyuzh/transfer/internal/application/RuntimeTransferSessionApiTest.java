package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.LookupTransferSessionResponse;
import com.yoyuzh.transfer.api.OfflineDownloadResult;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferImportApi;
import com.yoyuzh.transfer.api.TransferMode;
import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeTransferSessionApiTest {

    private OnlineTransferService onlineTransferService;
    private OfflineTransferService offlineTransferService;
    private TransferImportApi transferImportApi;
    private TransferRuntimeMetricsPort transferRuntimeMetricsPort;
    private TransferMetricsRecorder transferMetricsRecorder;
    private RuntimeTransferSessionApi transferSessionApi;

    @BeforeEach
    void setUp() {
        onlineTransferService = mock(OnlineTransferService.class);
        offlineTransferService = mock(OfflineTransferService.class);
        transferImportApi = mock(TransferImportApi.class);
        transferRuntimeMetricsPort = mock(TransferRuntimeMetricsPort.class);
        transferMetricsRecorder = new TransferMetricsRecorder(transferRuntimeMetricsPort);
        transferSessionApi = new RuntimeTransferSessionApi(
                onlineTransferService,
                offlineTransferService,
                transferImportApi,
                transferRuntimeMetricsPort,
                transferMetricsRecorder
        );
    }

    @Test
    void shouldRouteOnlineSessionCreationToOnlineService() {
        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.ONLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );
        TransferSessionResponse response = new TransferSessionResponse(
                "session-1",
                "123456",
                TransferMode.ONLINE,
                Instant.now().plusSeconds(60),
                command.files()
        );
        when(onlineTransferService.createSession(any())).thenReturn(response);

        TransferSessionResponse actual = transferSessionApi.createSession(null, command);

        assertThat(actual.sessionId()).isEqualTo("session-1");
        verify(transferRuntimeMetricsPort).recordTransferUsage(12L);
        verify(onlineTransferService).createSession(any());
        verify(onlineTransferService, never()).pruneExpiredSessions(any());
        verify(offlineTransferService, never()).pruneExpiredSessions(any());
    }

    @Test
    void shouldRequireAuthenticatedSenderForOfflineSessionCreation() {
        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );

        assertThatThrownBy(() -> transferSessionApi.createSession(null, command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_LOGGED_IN);
    }

    @Test
    void shouldRouteOfflineSessionCreationToOfflineService() {
        Long senderUserId = 7L;
        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );
        TransferSessionResponse response = new TransferSessionResponse(
                "offline-1",
                "654321",
                TransferMode.OFFLINE,
                Instant.now().plusSeconds(60),
                command.files()
        );
        when(offlineTransferService.createSession(anyLong(), any())).thenReturn(response);

        TransferSessionResponse actual = transferSessionApi.createSession(senderUserId, command);

        assertThat(actual.sessionId()).isEqualTo("offline-1");
        verify(transferRuntimeMetricsPort).recordTransferUsage(12L);
        verify(offlineTransferService).createSession(anyLong(), any());
    }

    @Test
    void shouldLookupOnlineSessionBeforeOfflineFallback() {
        LookupTransferSessionResponse response = new LookupTransferSessionResponse(
                "session-1",
                "AB12CD34",
                TransferMode.ONLINE,
                Instant.now().plusSeconds(60)
        );
        when(onlineTransferService.lookupSession("AB12CD34")).thenReturn(response);

        LookupTransferSessionResponse actual = transferSessionApi.lookupSession("AB12CD34");

        assertThat(actual.sessionId()).isEqualTo("session-1");
    }

    @Test
    void shouldReturnOfflineDownloadResultAndRecordTraffic() {
        OfflineDownloadResult response = OfflineDownloadResult.redirect("https://download.example.com/file");
        when(offlineTransferService.getReadyFileSize("session-1", "file-1")).thenReturn(128L);
        when(offlineTransferService.downloadOfflineFile("session-1", "file-1")).thenReturn(response);

        OfflineDownloadResult actual = transferSessionApi.downloadOfflineFile("session-1", "file-1");

        assertThat(actual.redirect()).isTrue();
        assertThat(actual.redirectUrl()).isEqualTo("https://download.example.com/file");
        verify(transferRuntimeMetricsPort).recordDownloadTraffic(128L);
        verify(offlineTransferService).downloadOfflineFile("session-1", "file-1");
        verify(onlineTransferService, never()).lookupSession(any());
        verify(onlineTransferService, never()).pruneExpiredSessions(any());
        verify(offlineTransferService, never()).pruneExpiredSessions(any());
    }

    @Test
    void shouldPruneSessionsOnlyDuringScheduledCleanup() {
        transferSessionApi.pruneExpiredTransfers();

        verify(onlineTransferService).pruneExpiredSessions(any());
        verify(offlineTransferService).pruneExpiredSessions(any());
    }

    @Test
    void shouldRecordTransferUsageOnlyAfterCommitWhenTransactionSynchronizationIsActive() {
        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.ONLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );
        TransferSessionResponse response = new TransferSessionResponse(
                "session-1",
                "AB12CD34",
                TransferMode.ONLINE,
                Instant.now().plusSeconds(60),
                command.files()
        );
        when(onlineTransferService.createSession(any())).thenReturn(response);

        TransactionSynchronizationManager.initSynchronization();
        try {
            transferSessionApi.createSession(null, command);

            verify(transferRuntimeMetricsPort, never()).recordTransferUsage(12L);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(transferRuntimeMetricsPort).recordTransferUsage(12L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
