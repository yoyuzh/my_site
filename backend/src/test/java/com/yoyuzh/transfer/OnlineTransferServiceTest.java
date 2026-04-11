package com.yoyuzh.transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineTransferServiceTest {

    private TransferSessionStore sessionStore;
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    private OnlineTransferService onlineTransferService;

    @BeforeEach
    void setUp() {
        sessionStore = mock(TransferSessionStore.class);
        offlineTransferSessionRepository = mock(OfflineTransferSessionRepository.class);
        onlineTransferService = new OnlineTransferService(sessionStore, offlineTransferSessionRepository);

        when(sessionStore.withSession(any(), any())).thenAnswer(invocation -> {
            TransferSession session = onlineSession();
            @SuppressWarnings("unchecked")
            Function<TransferSession, Object> action = (Function<TransferSession, Object>) invocation.getArgument(1);
            return action.apply(session);
        });
        when(sessionStore.findById(any())).thenReturn(Optional.of(onlineSession()));
    }

    @Test
    void shouldPersistUpdatedOnlineSessionInsideAtomicJoinOperation() {
        TransferSessionResponse response = onlineTransferService.joinSession("session-1");

        assertThat(response.sessionId()).isEqualTo("session-1");
        verify(sessionStore).withSession(any(), any());
        verify(sessionStore, never()).findById("session-1");
    }

    @Test
    void shouldPersistUpdatedOnlineSessionInsideAtomicSignalOperation() {
        onlineTransferService.postSignal("session-1", "sender", new TransferSignalRequest("offer", "{\"sdp\":\"demo\"}"));

        verify(sessionStore).withSession(any(), any());
        verify(sessionStore, never()).findById("session-1");
    }

    private TransferSession onlineSession() {
        return new TransferSession(
                "session-1",
                "123456",
                Instant.now().plusSeconds(300),
                List.of(new TransferFileItem("demo.txt", 12, "text/plain"))
        );
    }
}
