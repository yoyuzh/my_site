package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.OfflineDownloadResult;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferMode;
import com.yoyuzh.transfer.internal.domain.OfflineTransferFile;
import com.yoyuzh.transfer.internal.domain.OfflineTransferSession;
import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import com.yoyuzh.transfer.internal.infra.TransferSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineTransferServiceTest {

    @Mock
    private TransferSessionStore sessionStore;
    @Mock
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private OfflineTransferQuotaService offlineTransferQuotaService;

    private OfflineTransferService service;

    @BeforeEach
    void setUp() {
        FileStorageProperties props = new FileStorageProperties();
        service = new OfflineTransferService(
                sessionStore,
                offlineTransferSessionRepository,
                fileContentStorage,
                offlineTransferQuotaService,
                props
        );
    }

    // ── normalizePickupCode ────────────────────────────────────────────────

    @Test
    void shouldRejectPickupCodeWithFewerThanEightAlphaNumericCharacters() {
        assertThatThrownBy(() -> service.lookupReadySession("AB12"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid pickup code");
    }

    @Test
    void shouldStripSeparatorsAndUppercasePickupCode() {
        // but the session does not exist → FILE_NOT_FOUND
        when(offlineTransferSessionRepository.findWithFilesByPickupCode("AB12CD34"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookupReadySession("ab-12 cd34"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    // ── session expiry ─────────────────────────────────────────────────────

    @Test
    void shouldRejectLookupOfExpiredSession() {
        OfflineTransferSession expiredSession = buildReadySession(1L, "AB12CD34", Instant.now().minusSeconds(1));

        when(offlineTransferSessionRepository.findWithFilesByPickupCode("AB12CD34"))
                .thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(() -> service.lookupReadySession("AB12CD34"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void shouldRejectJoinOfExpiredSession() {
        String sessionId = UUID.randomUUID().toString();
        OfflineTransferSession expiredSession = buildReadySession(1L, "AB12CD34", Instant.now().minusSeconds(1));
        expiredSession.setSessionId(sessionId);

        when(offlineTransferSessionRepository.findWithFilesBySessionId(sessionId))
                .thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(() -> service.joinReadySession(sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_EXPIRED);
    }

    // ── uploadOfflineFile permission ───────────────────────────────────────

    @Test
    void shouldRejectUploadByNonOwner() {
        Long ownerId = 1L;
        Long intruderId = 2L;
        String sessionId = UUID.randomUUID().toString();
        OfflineTransferSession session = buildEditableSession(ownerId, sessionId);

        when(offlineTransferSessionRepository.findWithFilesBySessionId(sessionId))
                .thenReturn(Optional.of(session));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploadOfflineFile(intruderId, sessionId, session.getFiles().get(0).getId(), file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no permission");
    }

    @Test
    void shouldRejectUploadToExpiredSession() {
        Long ownerId = 1L;
        String sessionId = UUID.randomUUID().toString();
        OfflineTransferSession session = buildEditableSession(ownerId, sessionId);
        session.setExpiresAt(Instant.now().minusSeconds(1));

        when(offlineTransferSessionRepository.findWithFilesBySessionId(sessionId))
                .thenReturn(Optional.of(session));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploadOfflineFile(ownerId, sessionId, session.getFiles().get(0).getId(), file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_EXPIRED);
    }

    // ── normalizeLeafName / normalizeRelativePath ─────────────────────────

    @Test
    void shouldRejectSessionCreationWithEmptyFilename() {
        when(sessionStore.nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any())).thenReturn("AB12CD34");

        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem(null, "", null, 100L, "text/plain", null))
        );

        assertThatThrownBy(() -> service.createSession(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("file name cannot be empty");
    }

    @Test
    void shouldRejectSessionCreationWithPathTraversalInFilename() {
        when(sessionStore.nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any())).thenReturn("CD34EF56");

        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem(null, "../etc/passwd", null, 100L, "text/plain", null))
        );

        assertThatThrownBy(() -> service.createSession(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file name");
    }

    @Test
    void shouldRejectSessionCreationWithDotDotInRelativePath() {
        when(sessionStore.nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any())).thenReturn("EF56GH78");

        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem(null, "report.pdf", "valid/../../../etc/passwd", 100L, "application/pdf", null))
        );

        assertThatThrownBy(() -> service.createSession(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file path");
    }

    // ── state: not-ready session ──────────────────────────────────────────

    @Test
    void shouldRejectLookupOfNotYetReadySession() {
        OfflineTransferSession session = buildReadySession(1L, "GH78JK90", Instant.now().plusSeconds(3600));
        session.setReady(false); // override: not ready

        when(offlineTransferSessionRepository.findWithFilesByPickupCode("GH78JK90"))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.lookupReadySession("GH78JK90"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("still uploading");
    }

    @Test
    void shouldDownloadUploadedOfflineFileInlineWhenStorageCannotRedirect() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        OfflineTransferSession session = buildReadySession(1L, "LM12NP34", Instant.now().plusSeconds(3600));
        session.setSessionId(sessionId);
        OfflineTransferFile file = session.getFiles().get(0);
        byte[] content = "offline-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        file.setSize(content.length);

        when(offlineTransferSessionRepository.findWithFilesBySessionId(sessionId))
                .thenReturn(Optional.of(session));
        when(fileContentStorage.readTransferFileStream(sessionId, file.getStorageName()))
                .thenReturn(new ByteArrayInputStream(content));

        OfflineDownloadResult result = service.downloadOfflineFile(sessionId, file.getId());

        assertThat(result.redirect()).isFalse();
        assertThat(result.filename()).isEqualTo("report.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.contentLength()).isEqualTo(content.length);
        assertThat(result.body()).isNotNull();
        assertThat(result.body().getInputStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void shouldDownloadUploadedOfflineFileByRedirectWhenStorageSupportsDirectDownload() {
        String sessionId = UUID.randomUUID().toString();
        OfflineTransferSession session = buildReadySession(1L, "NP34QR56", Instant.now().plusSeconds(3600));
        session.setSessionId(sessionId);
        OfflineTransferFile file = session.getFiles().get(0);

        when(offlineTransferSessionRepository.findWithFilesBySessionId(sessionId))
                .thenReturn(Optional.of(session));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createTransferDownloadUrl(sessionId, file.getStorageName(), file.getFilename()))
                .thenReturn("https://cdn.example.test/offline/report.pdf");

        OfflineDownloadResult result = service.downloadOfflineFile(sessionId, file.getId());

        assertThat(result.redirect()).isTrue();
        assertThat(result.redirectUrl()).isEqualTo("https://cdn.example.test/offline/report.pdf");
        assertThat(result.body()).isNull();
        verify(fileContentStorage, never()).readTransferFileStream(anyString(), anyString());
    }

    @Test
    void shouldRejectDownloadWhenOfflineFileIsNotUploaded() {
        String sessionId = UUID.randomUUID().toString();
        OfflineTransferSession session = buildReadySession(1L, "QR56ST78", Instant.now().plusSeconds(3600));
        session.setSessionId(sessionId);
        OfflineTransferFile file = session.getFiles().get(0);
        file.setUploaded(false);

        when(offlineTransferSessionRepository.findWithFilesBySessionId(sessionId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.downloadOfflineFile(sessionId, file.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(fileContentStorage, never()).readTransferFileStream(anyString(), anyString());
        verify(fileContentStorage, never()).createTransferDownloadUrl(anyString(), anyString(), anyString());
    }

    @Test
    void shouldPruneOnlyUploadedFilesFromExpiredOfflineSessions() {
        Instant now = Instant.now();
        OfflineTransferSession session = buildReadySession(1L, "ST78UV90", now.minusSeconds(1));
        OfflineTransferFile uploaded = session.getFiles().get(0);
        OfflineTransferFile pending = buildFile("pending.txt");
        pending.setStorageName("pending.txt");
        pending.setUploaded(false);
        session.addFile(pending);

        when(offlineTransferSessionRepository.findAllExpiredWithFiles(now)).thenReturn(List.of(session));

        service.pruneExpiredSessions(now);

        verify(fileContentStorage).deleteTransferFile(session.getSessionId(), uploaded.getStorageName());
        verify(fileContentStorage, never()).deleteTransferFile(session.getSessionId(), pending.getStorageName());
        verify(offlineTransferSessionRepository).deleteAll(List.of(session));
    }

    @Test
    void shouldSkipPruneWhenNoOfflineSessionExpired() {
        Instant now = Instant.now();
        when(offlineTransferSessionRepository.findAllExpiredWithFiles(now)).thenReturn(List.of());

        service.pruneExpiredSessions(now);

        verify(fileContentStorage, never()).deleteTransferFile(anyString(), anyString());
        verify(offlineTransferSessionRepository, never()).deleteAll(any());
    }

    @Test
    void shouldRetryPickupCodeAllocationWhenCollisionExists() {
        when(sessionStore.nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any())).thenReturn("WX12YZ34");
        when(offlineTransferSessionRepository.save(any(OfflineTransferSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem(null, "report.pdf", null, 100L, "application/pdf", null))
        );

        assertThat(service.createSession(1L, command).pickupCode()).isEqualTo("WX12YZ34");
        verify(sessionStore).nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any());
    }

    @Test
    void shouldFailWhenPickupCodeAllocationKeepsColliding() {
        when(sessionStore.nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any()))
                .thenThrow(new IllegalStateException("unable to allocate pickup code"));

        CreateTransferSessionCommand command = new CreateTransferSessionCommand(
                TransferMode.OFFLINE,
                List.of(new TransferFileItem(null, "report.pdf", null, 100L, "application/pdf", null))
        );

        assertThatThrownBy(() -> service.createSession(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unable to allocate pickup code");

        verify(sessionStore).nextPickupCode(org.mockito.ArgumentMatchers.<Predicate<String>>any());
        verify(offlineTransferSessionRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private OfflineTransferSession buildReadySession(Long senderId, String pickupCode, Instant expiresAt) {
        OfflineTransferSession session = new OfflineTransferSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setPickupCode(pickupCode);
        session.setSenderUserId(senderId);
        session.setExpiresAt(expiresAt);
        session.setReady(true);

        OfflineTransferFile file = buildFile("report.pdf");
        file.setUploaded(true);
        session.addFile(file);
        return session;
    }

    private OfflineTransferSession buildEditableSession(Long senderId, String sessionId) {
        OfflineTransferSession session = new OfflineTransferSession();
        session.setSessionId(sessionId);
        session.setPickupCode("555555");
        session.setSenderUserId(senderId);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        session.setReady(false);
        session.addFile(buildFile("doc.pdf"));
        return session;
    }

    private OfflineTransferFile buildFile(String filename) {
        OfflineTransferFile file = new OfflineTransferFile();
        file.setId(UUID.randomUUID().toString());
        file.setFilename(filename);
        file.setRelativePath(filename);
        file.setSize(1024L);
        file.setContentType("application/pdf");
        file.setStorageName(file.getId() + ".pdf");
        file.setUploaded(false);
        return file;
    }
}
