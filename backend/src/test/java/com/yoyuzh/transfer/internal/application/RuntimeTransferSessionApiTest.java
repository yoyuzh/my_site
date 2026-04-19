package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.ops.admin.internal.application.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.LookupTransferSessionResponse;
import com.yoyuzh.transfer.OfflineTransferService;
import com.yoyuzh.transfer.OnlineTransferService;
import com.yoyuzh.transfer.TransferFileItem;
import com.yoyuzh.transfer.TransferMode;
import com.yoyuzh.transfer.TransferSessionResponse;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferImportApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeTransferSessionApiTest {

    private OnlineTransferService onlineTransferService;
    private OfflineTransferService offlineTransferService;
    private TransferImportApi transferImportApi;
    private AdminMetricsService adminMetricsService;
    private RuntimeTransferSessionApi transferSessionApi;

    @BeforeEach
    void setUp() {
        onlineTransferService = mock(OnlineTransferService.class);
        offlineTransferService = mock(OfflineTransferService.class);
        transferImportApi = mock(TransferImportApi.class);
        adminMetricsService = mock(AdminMetricsService.class);
        transferSessionApi = new RuntimeTransferSessionApi(
                onlineTransferService,
                offlineTransferService,
                transferImportApi,
                adminMetricsService
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
        verify(adminMetricsService).recordTransferUsage(12L);
        verify(onlineTransferService).createSession(any());
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
        User sender = new User();
        sender.setId(7L);
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
        when(offlineTransferService.createSession(any(User.class), any())).thenReturn(response);

        TransferSessionResponse actual = transferSessionApi.createSession(sender, command);

        assertThat(actual.sessionId()).isEqualTo("offline-1");
        verify(adminMetricsService).recordTransferUsage(12L);
        verify(offlineTransferService).createSession(any(User.class), any());
    }

    @Test
    void shouldLookupOnlineSessionBeforeOfflineFallback() {
        LookupTransferSessionResponse response = new LookupTransferSessionResponse(
                "session-1",
                "123456",
                TransferMode.ONLINE,
                Instant.now().plusSeconds(60)
        );
        when(onlineTransferService.lookupSession("123456")).thenReturn(response);

        LookupTransferSessionResponse actual = transferSessionApi.lookupSession("123456");

        assertThat(actual.sessionId()).isEqualTo("session-1");
    }
}
