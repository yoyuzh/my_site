package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.upload.internal.domain.UploadSession;
import com.yoyuzh.files.upload.internal.domain.UploadSessionRepository;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStateMachine;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStatus;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadSessionTusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectSessionPathOutsideTusRoot() {
        UploadSessionTusService service = new UploadSessionTusService(
                mock(UploadSessionRepository.class),
                new UploadSessionStateMachine(),
                mock(UploadSessionRuntimeStateService.class),
                mock(FileContentStorage.class),
                fixedClock(),
                tempDir
        );

        UploadSession session = createSession("../../escape", 5L);

        assertThatThrownBy(() -> service.currentOffset(session))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid tus upload path");
    }

    @Test
    void shouldRejectChunkThatExceedsDeclaredSessionSizeWithoutAdvancingOffset() {
        UploadSessionRepository repository = mock(UploadSessionRepository.class);
        when(repository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UploadSessionTusService service = new UploadSessionTusService(
                repository,
                new UploadSessionStateMachine(),
                mock(UploadSessionRuntimeStateService.class),
                mock(FileContentStorage.class),
                fixedClock(),
                tempDir
        );

        UploadSession session = createSession("session-1", 5L);
        service.start(session, 5L);

        assertThatThrownBy(() -> service.append(
                session,
                0L,
                new ByteArrayInputStream("123456".getBytes()),
                6L
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds declared session size");

        assertThat(service.currentOffset(session)).isZero();
    }

    @Test
    void shouldAcceptMatchingUploadLengthOutsideLongCacheRange() {
        UploadSessionTusService service = new UploadSessionTusService(
                mock(UploadSessionRepository.class),
                new UploadSessionStateMachine(),
                mock(UploadSessionRuntimeStateService.class),
                mock(FileContentStorage.class),
                fixedClock(),
                tempDir
        );

        UploadSession session = createSession("session-large", 20L * 1024 * 1024);

        long offset = service.start(session, Long.valueOf(20L * 1024 * 1024));

        assertThat(offset).isZero();
        assertThat(Files.exists(tempDir.resolve("session-large.bin"))).isTrue();
    }

    @Test
    void shouldDeleteTusTempFileAfterSuccessfulFinalize() throws Exception {
        FileContentStorage fileContentStorage = mock(FileContentStorage.class);
        UploadSessionRepository repository = mock(UploadSessionRepository.class);
        when(repository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UploadSessionTusService service = new UploadSessionTusService(
                repository,
                new UploadSessionStateMachine(),
                mock(UploadSessionRuntimeStateService.class),
                fileContentStorage,
                fixedClock(),
                tempDir
        );

        UploadSession session = createSession("session-1", 7L);
        service.start(session, 7L);
        service.append(session, 0L, new ByteArrayInputStream("payload".getBytes()), 7L);

        service.finalizeUpload(session);

        verify(fileContentStorage).storeBlob(eq("blobs/session-1"), eq("video/mp4"), any(), eq(7L));
        assertThat(Files.exists(tempDir.resolve("session-1.bin"))).isFalse();
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-04-08T06:00:00Z"), ZoneOffset.UTC);
    }

    private UploadSession createSession(String sessionId, long size) {
        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setUserId(7L);
        session.setTargetPath("/docs");
        session.setFilename("movie.mp4");
        session.setContentType("video/mp4");
        session.setSize(size);
        session.setObjectKey("blobs/" + sessionId);
        session.setChunkSize(size);
        session.setChunkCount(1);
        session.initializeCreated(LocalDateTime.of(2026, 4, 8, 6, 0), LocalDateTime.of(2026, 4, 9, 6, 0));
        return session;
    }
}
