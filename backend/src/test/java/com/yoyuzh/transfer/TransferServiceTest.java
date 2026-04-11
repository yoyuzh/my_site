package com.yoyuzh.transfer;

import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
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

class TransferServiceTest {

    private OnlineTransferService onlineTransferService;
    private OfflineTransferService offlineTransferService;
    private TransferImportService transferImportService;
    private AdminMetricsService adminMetricsService;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        onlineTransferService = mock(OnlineTransferService.class);
        offlineTransferService = mock(OfflineTransferService.class);
        transferImportService = mock(TransferImportService.class);
        adminMetricsService = mock(AdminMetricsService.class);
        transferService = new TransferService(
                onlineTransferService,
                offlineTransferService,
                transferImportService,
                adminMetricsService
        );
    }

    @Test
    void shouldRouteOnlineSessionCreationToOnlineService() {
        CreateTransferSessionRequest request = new CreateTransferSessionRequest(
                TransferMode.ONLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );
        TransferSessionResponse response = new TransferSessionResponse(
                "session-1",
                "123456",
                TransferMode.ONLINE,
                Instant.now().plusSeconds(60),
                request.files()
        );
        when(onlineTransferService.createSession(any(CreateTransferSessionRequest.class))).thenReturn(response);

        TransferSessionResponse actual = transferService.createSession(null, request);

        assertThat(actual.sessionId()).isEqualTo("session-1");
        verify(onlineTransferService).createSession(request);
        verify(adminMetricsService).recordTransferUsage(12L);
    }

    @Test
    void shouldRequireAuthenticatedSenderForOfflineSessionCreation() {
        CreateTransferSessionRequest request = new CreateTransferSessionRequest(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );

        assertThatThrownBy(() -> transferService.createSession(null, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_LOGGED_IN);
    }

    @Test
    void shouldRouteOfflineSessionCreationToOfflineService() {
        User sender = new User();
        sender.setId(7L);
        CreateTransferSessionRequest request = new CreateTransferSessionRequest(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );
        TransferSessionResponse response = new TransferSessionResponse(
                "offline-1",
                "654321",
                TransferMode.OFFLINE,
                Instant.now().plusSeconds(60),
                request.files()
        );
        when(offlineTransferService.createSession(any(User.class), any(CreateTransferSessionRequest.class))).thenReturn(response);

        TransferSessionResponse actual = transferService.createSession(sender, request);

        assertThat(actual.sessionId()).isEqualTo("offline-1");
        verify(offlineTransferService).createSession(sender, request);
        verify(adminMetricsService).recordTransferUsage(12L);
    }
}
