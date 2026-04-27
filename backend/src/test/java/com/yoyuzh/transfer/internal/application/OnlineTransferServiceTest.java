package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import com.yoyuzh.transfer.api.TransferSignalRequest;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.transfer.internal.domain.TransferSession;
import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import com.yoyuzh.transfer.internal.infra.TransferSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void shouldFailWhenPickupCodeCollidesTooManyTimes() {
        when(sessionStore.nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any()))
                .thenThrow(new IllegalStateException("unable to allocate pickup code"));

        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                com.yoyuzh.transfer.api.TransferMode.ONLINE,
                List.of(new TransferFileItem("demo.txt", 12L, "text/plain"))
        );

        assertThatThrownBy(() -> onlineTransferService.createSession(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unable to allocate pickup code");

        verify(sessionStore).nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any());
    }

    private TransferSession onlineSession() {
        return new TransferSession(
                "session-1",
                "AB12CD34",
                Instant.now().plusSeconds(300),
                List.of(new TransferFileItem("demo.txt", 12, "text/plain"))
        );
    }
}
