package com.yoyuzh.files.upload.internal.domain;

import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadSessionStateMachineTest {

    private final UploadSessionStateMachine stateMachine = new UploadSessionStateMachine();

    @Test
    void shouldMarkExpiredSessionAsExpired() {
        UploadSession session = createSession(UploadSessionStatus.UPLOADING);
        LocalDateTime now = LocalDateTime.of(2026, 4, 11, 12, 0);

        stateMachine.markExpired(session, now);

        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        assertThat(session.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void shouldRejectFurtherMultipartContentUploadWhenSessionAlreadyUploading() {
        UploadSession session = createSession(UploadSessionStatus.UPLOADING);

        assertThatThrownBy(() -> stateMachine.ensureCanReceiveContent(session, LocalDateTime.now(), true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldMoveCreatedSessionToUploading() {
        UploadSession session = createSession(UploadSessionStatus.CREATED);
        LocalDateTime now = LocalDateTime.of(2026, 4, 11, 12, 0);

        stateMachine.markUploading(session, now);

        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.UPLOADING);
        assertThat(session.getUpdatedAt()).isEqualTo(now);
    }

    private UploadSession createSession(UploadSessionStatus status) {
        UploadSession session = new UploadSession();
        session.initializeCreated(LocalDateTime.of(2026, 4, 11, 11, 0), LocalDateTime.of(2026, 4, 12, 11, 0));
        if (status != UploadSessionStatus.CREATED) {
            session.setStatus(status);
        }
        session.setUpdatedAt(LocalDateTime.of(2026, 4, 11, 11, 0));
        return session;
    }
}
