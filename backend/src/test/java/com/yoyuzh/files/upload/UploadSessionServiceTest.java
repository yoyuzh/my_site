package com.yoyuzh.files.upload.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.upload.api.UploadSessionTransportPolicy;
import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.upload.internal.domain.UploadSession;
import com.yoyuzh.files.upload.internal.domain.UploadSessionRepository;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStateMachine;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStatus;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.platform.storage.internal.domain.StoragePolicy;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UploadSessionServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private UploadSessionTransportPolicy uploadSessionTransportPolicy;
    @Mock
    private UploadSessionRuntimeStateService uploadSessionRuntimeStateService;
    @Mock
    private UploadTargetPolicy uploadTargetPolicy;
    @Mock
    private UploadCompletionApi uploadCompletionApi;
    @Mock
    private UploadSessionTusService uploadSessionTusService;

    private UploadSessionService uploadSessionService;

    @BeforeEach
    void setUp() {
        uploadSessionService = new UploadSessionService(
                uploadSessionRepository,
                uploadTargetPolicy,
                uploadCompletionApi,
                fileContentStorage,
                uploadSessionTransportPolicy,
                Clock.fixed(Instant.parse("2026-04-08T06:00:00Z"), ZoneOffset.UTC),
                new ObjectMapper(),
                new UploadPolicyResolver(
                        UploadPolicyResolver::resolveDefaultUploadMode,
                        UploadPolicyResolver::resolveDefaultEffectiveMaxUploadSize
                ),
                new UploadSessionStateMachine(),
                uploadSessionRuntimeStateService,
                uploadSessionTusService
        );
        lenient().when(uploadSessionTransportPolicy.resolveUploadMode(nullable(Long.class), nullable(String.class), nullable(Integer.class)))
                .thenAnswer(invocation -> {
                    Long storagePolicyId = invocation.getArgument(0);
                    String multipartUploadId = invocation.getArgument(1);
                    Integer chunkCount = invocation.getArgument(2);
                    if (storagePolicyId == null) {
                        if ((multipartUploadId != null && !multipartUploadId.isBlank()) || (chunkCount != null && chunkCount > 1)) {
                            return UploadSessionUploadMode.DIRECT_MULTIPART;
                        }
                        return UploadSessionUploadMode.PROXY;
                    }
                    return UploadSessionUploadMode.DIRECT_MULTIPART;
                });
        lenient().when(uploadSessionTransportPolicy.usesTusUpload(nullable(Long.class))).thenReturn(false);
    }

    @Test
    void shouldCreateUploadSessionWithoutChangingLegacyUploadPath() {
        IdentityAuthenticatedUser user = createUser(7L);
        StoragePolicy policy = createDefaultStoragePolicy();
        StoragePolicyCapabilities capabilities = new StoragePolicyCapabilities(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                500L * 1024 * 1024
        );
        when(uploadTargetPolicy.validateUpload(7L, user.maxUploadSizeBytes(), user.storageQuotaBytes(),
                "/docs", "movie.mp4", 20L * 1024 * 1024))
                .thenReturn(new ValidatedUploadTarget(
                        "/docs",
                        "movie.mp4",
                        new DefaultStoragePolicySnapshot(policy.getId(), policy.getMaxSizeBytes(), capabilities)
                ));
        when(fileContentStorage.createMultipartUpload(any(), eq("video/mp4"))).thenReturn("upload-123");
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> {
            UploadSession session = invocation.getArgument(0);
            session.setId(100L);
            return session;
        });

        UploadSessionView session = uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L * 1024 * 1024)
        );

        assertThat(session.sessionId()).isNotBlank();
        assertThat(session.objectKey()).startsWith("blobs/");
        assertThat(session.status()).isEqualTo(UploadSessionStatus.CREATED);
        assertThat(session.storagePolicyId()).isEqualTo(42L);
        assertThat(session.chunkSize()).isEqualTo(8L * 1024 * 1024);
        assertThat(session.chunkCount()).isEqualTo(3);
        assertThat(session.expiresAt()).isEqualTo(LocalDateTime.of(2026, 4, 9, 6, 0));
        verify(uploadSessionRuntimeStateService).markCreated(any(UploadSession.class));
    }

    @Test
    void shouldPrepareMultipartPartUploadForOwnedSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setMultipartUploadId("upload-123");
        session.setChunkCount(3);
        session.setChunkSize(8L * 1024 * 1024);
        session.setSize(20L * 1024 * 1024);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(fileContentStorage.prepareMultipartPartUpload(
                "blobs/session-1",
                "upload-123",
                3,
                "video/mp4",
                4L * 1024 * 1024
        )).thenReturn(new PreparedUpload(
                true,
                "https://upload.example.com/session-1/part-3",
                "PUT",
                Map.of("Content-Type", "video/mp4"),
                "blobs/session-1"
        ));

        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedPartUpload(user.id(), "session-1", 2);

        assertThat(preparedUpload.uploadUrl()).isEqualTo("https://upload.example.com/session-1/part-3");
        assertThat(preparedUpload.method()).isEqualTo("PUT");
    }

    @Test
    void shouldAllowLegacyMultipartPrepareRetryAfterCompletionStarted() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.COMPLETING);
        session.setMultipartUploadId("upload-123");
        session.setChunkCount(1);
        session.setChunkSize(8L * 1024 * 1024);
        session.setSize(20L);
        session.setUploadedPartsJson("""
                [
                  {"partIndex":0,"etag":"etag-1","size":20,"uploadedAt":"2026-04-08T06:00:00"}
                ]
                """);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(fileContentStorage.prepareMultipartPartUpload(
                "blobs/session-1",
                "upload-123",
                1,
                "video/mp4",
                20L
        )).thenReturn(new PreparedUpload(
                true,
                "https://upload.example.com/session-1/part-1",
                "PUT",
                Map.of("Content-Type", "video/mp4"),
                "blobs/session-1"
        ));

        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedPartUpload(user.id(), "session-1", 0);

        assertThat(preparedUpload.uploadUrl()).isEqualTo("https://upload.example.com/session-1/part-1");
        verify(fileContentStorage, never()).createMultipartUpload(any(), any());
    }

    @Test
    void shouldPrepareDirectSingleUploadForOwnedSessionWhenPolicyDisablesMultipart() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStoragePolicyId(42L);
        session.setMultipartUploadId(null);
        session.setChunkCount(1);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionTransportPolicy.resolveUploadMode(42L, null, 1)).thenReturn(UploadSessionUploadMode.DIRECT_SINGLE);
        when(fileContentStorage.prepareBlobUpload("/docs", "movie.mp4", "blobs/session-1", "video/mp4", 20L))
                .thenReturn(new PreparedUpload(
                        true,
                        "https://upload.example.com/session-1",
                        "PUT",
                        Map.of("Content-Type", "video/mp4"),
                        "blobs/session-1"
                ));

        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedUpload(user.id(), "session-1");

        assertThat(preparedUpload.direct()).isTrue();
        assertThat(preparedUpload.uploadUrl()).isEqualTo("https://upload.example.com/session-1");
        assertThat(preparedUpload.method()).isEqualTo("PUT");
    }

    @Test
    void shouldUploadProxyContentForOwnedSessionWhenPolicyDisablesDirectUpload() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStoragePolicyId(42L);
        session.setMultipartUploadId(null);
        session.setChunkCount(1);
        session.setSize(7L);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionTransportPolicy.resolveUploadMode(42L, null, 1)).thenReturn(UploadSessionUploadMode.PROXY);
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionView result = uploadSessionService.uploadOwnedContent(
                user.id(),
                "session-1",
                new MockMultipartFile("file", "movie.mp4", "video/mp4", "payload".getBytes())
        );

        assertThat(result.status()).isEqualTo(UploadSessionStatus.UPLOADING);
        verify(fileContentStorage).uploadBlob(eq("blobs/session-1"), any(MockMultipartFile.class));
    }

    @Test
    void shouldCreateProxyUploadSessionWhenPolicyDisablesDirectUpload() {
        IdentityAuthenticatedUser user = createUser(7L);
        StoragePolicy policy = createDefaultStoragePolicy();
        when(uploadTargetPolicy.validateUpload(7L, user.maxUploadSizeBytes(), user.storageQuotaBytes(),
                "/docs", "movie.mp4", 20L))
                .thenReturn(new ValidatedUploadTarget(
                        "/docs",
                        "movie.mp4",
                        new DefaultStoragePolicySnapshot(policy.getId(), policy.getMaxSizeBytes(), new StoragePolicyCapabilities(
                                false,
                                false,
                                false,
                                true,
                                false,
                                true,
                                false,
                                false,
                                500L * 1024 * 1024
                        ))
                ));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> {
            UploadSession saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        UploadSessionView session = uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L)
        );

        assertThat(session.chunkCount()).isEqualTo(1);
    }

    @Test
    void shouldOnlyReturnSessionOwnedByCurrentUser() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(user.id());
        session.initializeCreated(LocalDateTime.of(2026, 4, 8, 6, 0), LocalDateTime.of(2026, 4, 9, 6, 0));
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        UploadSessionView result = uploadSessionService.getOwnedSession(user.id(), "session-1");

        assertThat(result.sessionId()).isEqualTo("session-1");
    }

    @Test
    void shouldRejectDuplicateTargetWhenCreatingSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        when(uploadTargetPolicy.validateUpload(7L, user.maxUploadSizeBytes(), user.storageQuotaBytes(),
                "/docs", "movie.mp4", 20L))
                .thenThrow(new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.UNKNOWN, "duplicate"));

        assertThatThrownBy(() -> uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCompleteOwnedSessionThroughUploadCompletionApi() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setMultipartUploadId("upload-123");
        session.setChunkCount(2);
        session.setChunkSize(8L * 1024 * 1024);
        session.setUploadedPartsJson("""
                [
                  {"partIndex":0,"etag":"etag-1","size":8388608,"uploadedAt":"2026-04-08T06:00:00"},
                  {"partIndex":1,"etag":"etag-2","size":12,"uploadedAt":"2026-04-08T06:01:00"}
                ]
                """);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionView result = uploadSessionService.completeOwnedSession(user.id(), "session-1");

        assertThat(result.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 6, 0));
        verify(fileContentStorage).completeMultipartUpload(eq("blobs/session-1"), eq("upload-123"), anyList());
        ArgumentCaptor<UploadCompletionCommand> commandCaptor = ArgumentCaptor.forClass(UploadCompletionCommand.class);
        verify(uploadCompletionApi).completeStoredBlob(commandCaptor.capture());
        assertThat(commandCaptor.getValue().userId()).isEqualTo(7L);
        assertThat(commandCaptor.getValue().normalizedPath()).isEqualTo("/docs");
        assertThat(commandCaptor.getValue().filename()).isEqualTo("movie.mp4");
        assertThat(commandCaptor.getValue().objectKey()).isEqualTo("blobs/session-1");
        assertThat(commandCaptor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(commandCaptor.getValue().size()).isEqualTo(20L);
        verify(uploadSessionRuntimeStateService).markCompleted(any(UploadSession.class), eq(LocalDateTime.of(2026, 4, 8, 6, 0)));
    }

    @Test
    void shouldRejectCompletingCancelledSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.CANCELLED);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.completeOwnedSession(user.id(), "session-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldReturnCompletedSessionAsIsWhenCompletingAgain() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.COMPLETED);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        UploadSessionView result = uploadSessionService.completeOwnedSession(user.id(), "session-1");

        assertThat(result.sessionId()).isEqualTo(session.getSessionId());
        verify(uploadCompletionApi, never()).completeStoredBlob(any());
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void shouldExpireSessionWhenCompletingAfterExpiry() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setExpiresAt(LocalDateTime.of(2026, 4, 8, 5, 59));
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> uploadSessionService.completeOwnedSession(user.id(), "session-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("upload session has expired");

        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionRuntimeStateService).markExpired(session, LocalDateTime.of(2026, 4, 8, 6, 0));
        verify(uploadCompletionApi, never()).completeStoredBlob(any());
    }

    @Test
    void shouldMarkFailedWhenCompletionApiFails() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("metadata failed"))
                .when(uploadCompletionApi)
                .completeStoredBlob(any());

        assertThatThrownBy(() -> uploadSessionService.completeOwnedSession(user.id(), "session-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata failed");

        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.FAILED);
        verify(uploadSessionRuntimeStateService).markFailed(session, LocalDateTime.of(2026, 4, 8, 6, 0));
    }

    @Test
    void shouldCompleteTusBackedSessionThroughTusFinalizeAndBlobCompletion() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.UPLOADING);
        session.setStoragePolicyId(42L);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionTransportPolicy.resolveUploadMode(42L, null, 1)).thenReturn(UploadSessionUploadMode.PROXY);
        when(uploadSessionTransportPolicy.usesTusUpload(42L)).thenReturn(true);

        UploadSessionView result = uploadSessionService.completeOwnedSession(user.id(), "session-1");

        assertThat(result.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        verify(uploadSessionTusService).finalizeUpload(session);
        verify(fileContentStorage, never()).completeBlobUpload(any(), any(), anyLong());
        verify(fileContentStorage, never()).completeMultipartUpload(any(), any(), anyList());
        verify(uploadCompletionApi).completeStoredBlob(any(UploadCompletionCommand.class));
    }

    @Test
    void shouldRejectStartingTusSessionWhenStoragePolicyIsNotTusBacked() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStoragePolicyId(42L);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionTransportPolicy.usesTusUpload(42L)).thenReturn(false);

        assertThatThrownBy(() -> uploadSessionService.startTusSession(user.id(), "session-1", 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not support tus upload");

        verify(uploadSessionTusService, never()).start(any(), any());
    }

    @Test
    void shouldRejectCompletingIncompleteMultipartSessionAndMarkFailed() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setMultipartUploadId("upload-123");
        session.setChunkCount(2);
        session.setChunkSize(8L * 1024 * 1024);
        session.setUploadedPartsJson("""
                [
                  {"partIndex":0,"etag":"etag-1","size":8388608,"uploadedAt":"2026-04-08T06:00:00"}
                ]
                """);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> uploadSessionService.completeOwnedSession(user.id(), "session-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("multipart upload is incomplete");

        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.FAILED);
        verify(fileContentStorage, never()).completeMultipartUpload(any(), any(), anyList());
        verify(uploadCompletionApi, never()).completeStoredBlob(any());
    }

    @Test
    void shouldRecordUploadedPartAndMoveSessionToUploading() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setChunkCount(3);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionView result = uploadSessionService.recordUploadedPart(
                user.id(),
                "session-1",
                1,
                new UploadSessionPartCommand("etag-1", 8L * 1024 * 1024)
        );

        assertThat(result.status()).isEqualTo(UploadSessionStatus.UPLOADING);
        assertThat(session.getUploadedPartsJson()).contains("\"partIndex\":1");
        assertThat(session.getUploadedPartsJson()).contains("\"etag\":\"etag-1\"");
        assertThat(session.getUploadedPartsJson()).contains("\"size\":8388608");

        UploadSessionView secondResult = uploadSessionService.recordUploadedPart(
                user.id(),
                "session-1",
                2,
                new UploadSessionPartCommand("etag-2", 4L)
        );

        assertThat(session.getUploadedPartsJson()).contains("\"partIndex\":1");
        assertThat(session.getUploadedPartsJson()).contains("\"partIndex\":2");
        assertThat(session.getUploadedPartsJson()).contains("\"etag\":\"etag-2\"");
        verify(uploadSessionRuntimeStateService).markUploading(any(UploadSession.class), eq(8L * 1024 * 1024), eq(1), eq(LocalDateTime.of(2026, 4, 8, 6, 0)));
        verify(uploadSessionRuntimeStateService).markUploading(any(UploadSession.class), eq(8L * 1024 * 1024 + 4L), eq(2), eq(LocalDateTime.of(2026, 4, 8, 6, 0)));
    }

    @Test
    void shouldTreatLegacyMultipartRecordedPartRetryAsIdempotent() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.COMPLETED);
        session.setMultipartUploadId("upload-123");
        session.setChunkCount(1);
        session.setSize(20L);
        session.setUploadedPartsJson("""
                [
                  {"partIndex":0,"etag":"etag-1","size":20,"uploadedAt":"2026-04-08T06:00:00"}
                ]
                """);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        UploadSessionView result = uploadSessionService.recordUploadedPart(
                user.id(),
                "session-1",
                0,
                new UploadSessionPartCommand("etag-1", 20L)
        );

        assertThat(result.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        verify(uploadSessionRepository, never()).save(any(UploadSession.class));
    }

    @Test
    void shouldRejectUploadedPartOutsideSessionRange() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setChunkCount(3);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.recordUploadedPart(
                user.id(),
                "session-1",
                3,
                new UploadSessionPartCommand("etag-3", 1L)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectUploadedPartWithoutEtag() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setChunkCount(3);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.recordUploadedPart(
                user.id(),
                "session-1",
                1,
                new UploadSessionPartCommand(" ", 1L)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("part etag is required");
    }

    @Test
    void shouldRejectUploadedPartWithNegativeSize() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setChunkCount(3);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.recordUploadedPart(
                user.id(),
                "session-1",
                1,
                new UploadSessionPartCommand("etag-1", -1L)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid part size");
    }

    @Test
    void shouldCancelOwnedSessionAndUpdateRuntimeState() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionView result = uploadSessionService.cancelOwnedSession(user.id(), "session-1");

        assertThat(result.status()).isEqualTo(UploadSessionStatus.CANCELLED);
        verify(fileContentStorage).deleteBlob("blobs/session-1");
        verify(uploadSessionRuntimeStateService).markCancelled(any(UploadSession.class), eq(LocalDateTime.of(2026, 4, 8, 6, 0)));
    }

    @Test
    void shouldAbortMultipartUploadWhenCancellingOwnedSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setMultipartUploadId("upload-123");
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionView result = uploadSessionService.cancelOwnedSession(user.id(), "session-1");

        assertThat(result.status()).isEqualTo(UploadSessionStatus.CANCELLED);
        verify(fileContentStorage).abortMultipartUpload("blobs/session-1", "upload-123");
        verify(fileContentStorage, never()).deleteBlob(any());
    }

    @Test
    void shouldDeleteTusStateWhenCancellingOwnedSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStoragePolicyId(42L);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionTransportPolicy.usesTusUpload(42L)).thenReturn(true);

        UploadSessionView result = uploadSessionService.cancelOwnedSession(user.id(), "session-1");

        assertThat(result.status()).isEqualTo(UploadSessionStatus.CANCELLED);
        verify(uploadSessionTusService).delete(session);
        verify(fileContentStorage, never()).deleteBlob(any());
        verify(fileContentStorage, never()).abortMultipartUpload(any(), any());
    }

    @Test
    void shouldRejectCancellingCompletedSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.COMPLETED);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.cancelOwnedSession(user.id(), "session-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("completed upload session cannot be cancelled");

        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void shouldRejectProxyUploadWhenContentSizeDoesNotMatchSession() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        session.setSize(7L);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.uploadOwnedContent(
                user.id(),
                "session-1",
                new MockMultipartFile("file", "movie.mp4", "video/mp4", "short".getBytes())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("upload size does not match session");

        verify(fileContentStorage, never()).uploadBlob(any(), any());
    }

    @Test
    void shouldRejectProxyUploadWhenContentIsMissing() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSession(user);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.uploadOwnedContent(user.id(), "session-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("upload content is required");

        verify(fileContentStorage, never()).uploadBlob(any(), any());
    }

    @Test
    void shouldExpireUnfinishedSessionsAndDeleteTemporaryBlobs() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.UPLOADING);
        session.setObjectKey("blobs/expired-session");
        session.setMultipartUploadId("upload-expired");
        session.setExpiresAt(LocalDateTime.of(2026, 4, 8, 5, 0));
        when(uploadSessionRepository.findByStatusInAndExpiresAtBefore(anyList(), eq(LocalDateTime.of(2026, 4, 8, 6, 0))))
                .thenReturn(List.of(session));

        int expiredCount = uploadSessionService.pruneExpiredSessions();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        assertThat(session.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 6, 0));
        verify(fileContentStorage).abortMultipartUpload("blobs/expired-session", "upload-expired");
        verify(uploadSessionRepository).saveAll(List.of(session));
    }

    @Test
    void shouldExpireUnfinishedSingleBlobSessionsAndIgnoreCleanupFailure() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.CREATED);
        session.setObjectKey("blobs/expired-single");
        session.setMultipartUploadId(null);
        session.setExpiresAt(LocalDateTime.of(2026, 4, 8, 5, 0));
        when(uploadSessionRepository.findByStatusInAndExpiresAtBefore(anyList(), eq(LocalDateTime.of(2026, 4, 8, 6, 0))))
                .thenReturn(List.of(session));
        org.mockito.Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(fileContentStorage)
                .deleteBlob("blobs/expired-single");

        int expiredCount = uploadSessionService.pruneExpiredSessions();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepository).saveAll(List.of(session));
        verify(uploadSessionRuntimeStateService).markExpired(session, LocalDateTime.of(2026, 4, 8, 6, 0));
    }

    @Test
    void shouldExpireTusBackedSessionsAndDeleteTusTempState() {
        IdentityAuthenticatedUser user = createUser(7L);
        UploadSession session = createSessionWithStatus(user, UploadSessionStatus.UPLOADING);
        session.setStoragePolicyId(42L);
        session.setObjectKey("blobs/expired-tus");
        session.setMultipartUploadId(null);
        session.setExpiresAt(LocalDateTime.of(2026, 4, 8, 5, 0));
        when(uploadSessionRepository.findByStatusInAndExpiresAtBefore(anyList(), eq(LocalDateTime.of(2026, 4, 8, 6, 0))))
                .thenReturn(List.of(session));
        when(uploadSessionTransportPolicy.usesTusUpload(42L)).thenReturn(true);

        int expiredCount = uploadSessionService.pruneExpiredSessions();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionTusService).delete(session);
        verify(fileContentStorage, never()).deleteBlob(any());
        verify(fileContentStorage, never()).abortMultipartUpload(any(), any());
    }

    private IdentityAuthenticatedUser createUser(Long id) {
        return new IdentityAuthenticatedUser(
                id,
                "user-" + id,
                "encoded",
                IdentityRoleName.USER,
                false,
                "session-" + id,
                "session-" + id,
                null,
                1024L * 1024 * 1024,
                100L * 1024 * 1024
        );
    }

    private StoragePolicy createDefaultStoragePolicy() {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(42L);
        policy.setName("Default S3 Compatible Storage");
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setMaxSizeBytes(500L * 1024 * 1024);
        policy.setEnabled(true);
        policy.setDefaultPolicy(true);
        return policy;
    }

    private UploadSession createSession(IdentityAuthenticatedUser user) {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUserId(user.id());
        session.setTargetPath("/docs");
        session.setFilename("movie.mp4");
        session.setContentType("video/mp4");
        session.setSize(20L);
        session.setObjectKey("blobs/session-1");
        session.setMultipartUploadId(null);
        session.setChunkSize(8L * 1024 * 1024);
        session.setChunkCount(1);
        session.setUploadedPartsJson("[]");
        session.initializeCreated(LocalDateTime.of(2026, 4, 8, 6, 0), LocalDateTime.of(2026, 4, 9, 6, 0));
        return session;
    }

    private UploadSession createSessionWithStatus(IdentityAuthenticatedUser user, UploadSessionStatus status) {
        UploadSession session = createSession(user);
        UploadSessionStateMachine stateMachine = new UploadSessionStateMachine();
        LocalDateTime now = LocalDateTime.of(2026, 4, 8, 6, 0);
        switch (status) {
            case CREATED -> {
            }
            case UPLOADING -> stateMachine.markUploading(session, now);
            case COMPLETING -> stateMachine.markCompleting(session, now);
            case COMPLETED -> stateMachine.markCompleted(session, now);
            case FAILED -> stateMachine.markFailed(session, now);
            case CANCELLED -> stateMachine.markCancelled(session, now);
            case EXPIRED -> stateMachine.markExpired(session, now);
        }
        return session;
    }
}
