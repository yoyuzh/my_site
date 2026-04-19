package com.yoyuzh.transfer;

import com.yoyuzh.auth.User;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferImportCommand;
import com.yoyuzh.transfer.api.TransferSessionApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private TransferSessionApi transferSessionApi;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferSessionApi = mock(TransferSessionApi.class);
        transferService = new TransferService(transferSessionApi);
    }

    @Test
    void shouldDelegateSessionCreationToTransferApi() {
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
        when(transferSessionApi.createSession(any(), any(CreateTransferSessionCommand.class))).thenReturn(response);

        TransferSessionResponse actual = transferService.createSession(null, request);

        assertThat(actual.sessionId()).isEqualTo("session-1");
        verify(transferSessionApi).createSession(null, new CreateTransferSessionCommand(request.mode(), request.files()));
    }

    @Test
    void shouldDelegateOfflineImportToTransferApi() {
        User recipient = new User();
        recipient.setId(7L);
        FileMetadataResponse response = new FileMetadataResponse(10L, "offline.txt", "/docs", 12L, "text/plain", false, null);
        when(transferSessionApi.importOfflineFile(recipient, "session-1", "file-1", new TransferImportCommand("/docs"))).thenReturn(response);

        FileMetadataResponse actual = transferService.importOfflineFile(recipient, "session-1", "file-1", "/docs");

        assertThat(actual.id()).isEqualTo(10L);
        verify(transferSessionApi).importOfflineFile(recipient, "session-1", "file-1", new TransferImportCommand("/docs"));
    }

    @Test
    void shouldDelegateUploadAndDownloadToTransferApi() {
        User sender = new User();
        sender.setId(7L);
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "demo".getBytes());
        ResponseEntity<?> response = ResponseEntity.ok("ok");
        doReturn(response).when(transferSessionApi).downloadOfflineFile("session-1", "file-1");

        transferService.uploadOfflineFile(sender, "session-1", "file-1", file);
        ResponseEntity<?> actual = transferService.downloadOfflineFile("session-1", "file-1");

        assertThat(actual.getBody()).isEqualTo("ok");
        verify(transferSessionApi).uploadOfflineFile(sender, "session-1", "file-1", file);
        verify(transferSessionApi).downloadOfflineFile("session-1", "file-1");
    }
}
