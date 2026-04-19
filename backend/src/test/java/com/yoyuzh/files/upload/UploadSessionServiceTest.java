package com.yoyuzh.files.upload;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.platform.storage.api.DefaultStoragePolicySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadSessionServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private FileContentStorage fileContentStorage;
    @Mock
    private StoragePolicyService storagePolicyService;
    @Mock
    private UploadSessionRuntimeStateService uploadSessionRuntimeStateService;
    @Mock
    private UploadTargetPolicy uploadTargetPolicy;
    @Mock
    private UploadCompletionApi uploadCompletionApi;

    private UploadSessionService uploadSessionService;

    @BeforeEach
    void setUp() {
        uploadSessionService = new UploadSessionService(
                uploadSessionRepository,
                uploadTargetPolicy,
                uploadCompletionApi,
                fileContentStorage,
                storagePolicyService,
                Clock.fixed(Instant.parse("2026-04-08T06:00:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(uploadSessionService, "uploadSessionRuntimeStateService", uploadSessionRuntimeStateService);
    }

    @Test
    void shouldCreateUploadSessionWithoutChangingLegacyUploadPath() {
        User user = createUser(7L);
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
        when(uploadTargetPolicy.validateUpload(user, "/docs", "movie.mp4", 20L * 1024 * 1024))
                .thenReturn(new ValidatedUploadTarget(
                        "/docs",
                        "movie.mp4",
                        new DefaultStoragePolicySnapshot(policy, capabilities)
                ));
        when(fileContentStorage.createMultipartUpload(any(), eq("video/mp4"))).thenReturn("upload-123");
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> {
            UploadSession session = invocation.getArgument(0);
            session.setId(100L);
            return session;
        });

        UploadSession session = uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L * 1024 * 1024)
        );

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getObjectKey()).startsWith("blobs/");
        assertThat(session.getMultipartUploadId()).isEqualTo("upload-123");
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.CREATED);
        assertThat(session.getStoragePolicyId()).isEqualTo(42L);
        assertThat(session.getChunkSize()).isEqualTo(8L * 1024 * 1024);
        assertThat(session.getChunkCount()).isEqualTo(3);
        assertThat(session.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 4, 9, 6, 0));
        verify(uploadSessionRuntimeStateService).markCreated(session);
    }

    @Test
    void shouldPrepareMultipartPartUploadForOwnedSession() {
        User user = createUser(7L);
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

        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedPartUpload(user, "session-1", 2);

        assertThat(preparedUpload.uploadUrl()).isEqualTo("https://upload.example.com/session-1/part-3");
        assertThat(preparedUpload.method()).isEqualTo("PUT");
    }

    @Test
    void shouldPrepareDirectSingleUploadForOwnedSessionWhenPolicyDisablesMultipart() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStoragePolicyId(42L);
        session.setMultipartUploadId(null);
        session.setChunkCount(1);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        StoragePolicy policy = createDefaultStoragePolicy();
        when(storagePolicyService.getRequiredPolicy(42L)).thenReturn(policy);
        when(storagePolicyService.readCapabilities(policy)).thenReturn(new StoragePolicyCapabilities(
                true,
                false,
                true,
                true,
                false,
                true,
                true,
                false,
                500L * 1024 * 1024
        ));
        when(storagePolicyService.resolveUploadMode(any())).thenReturn(UploadSessionUploadMode.DIRECT_SINGLE);
        when(fileContentStorage.prepareBlobUpload("/docs", "movie.mp4", "blobs/session-1", "video/mp4", 20L))
                .thenReturn(new PreparedUpload(
                        true,
                        "https://upload.example.com/session-1",
                        "PUT",
                        Map.of("Content-Type", "video/mp4"),
                        "blobs/session-1"
                ));

        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedUpload(user, "session-1");

        assertThat(preparedUpload.direct()).isTrue();
        assertThat(preparedUpload.uploadUrl()).isEqualTo("https://upload.example.com/session-1");
        assertThat(preparedUpload.method()).isEqualTo("PUT");
    }

    @Test
    void shouldUploadProxyContentForOwnedSessionWhenPolicyDisablesDirectUpload() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStoragePolicyId(42L);
        session.setMultipartUploadId(null);
        session.setChunkCount(1);
        session.setSize(7L);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        StoragePolicy policy = createDefaultStoragePolicy();
        when(storagePolicyService.getRequiredPolicy(42L)).thenReturn(policy);
        when(storagePolicyService.readCapabilities(policy)).thenReturn(new StoragePolicyCapabilities(
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                500L * 1024 * 1024
        ));
        when(storagePolicyService.resolveUploadMode(any())).thenReturn(UploadSessionUploadMode.PROXY);
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSession result = uploadSessionService.uploadOwnedContent(
                user,
                "session-1",
                new MockMultipartFile("file", "movie.mp4", "video/mp4", "payload".getBytes())
        );

        assertThat(result.getStatus()).isEqualTo(UploadSessionStatus.UPLOADING);
        verify(fileContentStorage).uploadBlob(eq("blobs/session-1"), any(MockMultipartFile.class));
    }

    @Test
    void shouldCreateProxyUploadSessionWhenPolicyDisablesDirectUpload() {
        User user = createUser(7L);
        StoragePolicy policy = createDefaultStoragePolicy();
        when(uploadTargetPolicy.validateUpload(user, "/docs", "movie.mp4", 20L))
                .thenReturn(new ValidatedUploadTarget(
                        "/docs",
                        "movie.mp4",
                        new DefaultStoragePolicySnapshot(policy, new StoragePolicyCapabilities(
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

        UploadSession session = uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L)
        );

        assertThat(session.getMultipartUploadId()).isNull();
        assertThat(session.getChunkCount()).isEqualTo(1);
    }

    @Test
    void shouldOnlyReturnSessionOwnedByCurrentUser() {
        User user = createUser(7L);
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUser(user);
        session.setStatus(UploadSessionStatus.CREATED);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        UploadSession result = uploadSessionService.getOwnedSession(user, "session-1");

        assertThat(result).isSameAs(session);
    }

    @Test
    void shouldRejectDuplicateTargetWhenCreatingSession() {
        User user = createUser(7L);
        when(uploadTargetPolicy.validateUpload(user, "/docs", "movie.mp4", 20L))
                .thenThrow(new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.UNKNOWN, "duplicate"));

        assertThatThrownBy(() -> uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCompleteOwnedSessionThroughUploadCompletionApi() {
        User user = createUser(7L);
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

        UploadSession result = uploadSessionService.completeOwnedSession(user, "session-1");

        assertThat(result.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
        assertThat(result.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 6, 0));
        verify(fileContentStorage).completeMultipartUpload(eq("blobs/session-1"), eq("upload-123"), anyList());
        ArgumentCaptor<UploadCompletionCommand> commandCaptor = ArgumentCaptor.forClass(UploadCompletionCommand.class);
        verify(uploadCompletionApi).completeStoredBlob(commandCaptor.capture());
        assertThat(commandCaptor.getValue().normalizedPath()).isEqualTo("/docs");
        assertThat(commandCaptor.getValue().filename()).isEqualTo("movie.mp4");
        assertThat(commandCaptor.getValue().objectKey()).isEqualTo("blobs/session-1");
        assertThat(commandCaptor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(commandCaptor.getValue().size()).isEqualTo(20L);
        verify(uploadSessionRuntimeStateService).markCompleted(result, LocalDateTime.of(2026, 4, 8, 6, 0));
    }

    @Test
    void shouldRejectCompletingCancelledSession() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStatus(UploadSessionStatus.CANCELLED);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.completeOwnedSession(user, "session-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRecordUploadedPartAndMoveSessionToUploading() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        session.setChunkCount(3);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSession result = uploadSessionService.recordUploadedPart(
                user,
                "session-1",
                1,
                new UploadSessionPartCommand("etag-1", 8L * 1024 * 1024)
        );

        assertThat(result.getStatus()).isEqualTo(UploadSessionStatus.UPLOADING);
        assertThat(result.getUploadedPartsJson()).contains("\"partIndex\":1");
        assertThat(result.getUploadedPartsJson()).contains("\"etag\":\"etag-1\"");
        assertThat(result.getUploadedPartsJson()).contains("\"size\":8388608");

        UploadSession secondResult = uploadSessionService.recordUploadedPart(
                user,
                "session-1",
                2,
                new UploadSessionPartCommand("etag-2", 4L)
        );

        assertThat(secondResult.getUploadedPartsJson()).contains("\"partIndex\":1");
        assertThat(secondResult.getUploadedPartsJson()).contains("\"partIndex\":2");
        assertThat(secondResult.getUploadedPartsJson()).contains("\"etag\":\"etag-2\"");
        verify(uploadSessionRuntimeStateService).markUploading(result, 8L * 1024 * 1024, 1, LocalDateTime.of(2026, 4, 8, 6, 0));
        verify(uploadSessionRuntimeStateService).markUploading(secondResult, 8L * 1024 * 1024 + 4L, 2, LocalDateTime.of(2026, 4, 8, 6, 0));
    }

    @Test
    void shouldRejectUploadedPartOutsideSessionRange() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        session.setChunkCount(3);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> uploadSessionService.recordUploadedPart(
                user,
                "session-1",
                3,
                new UploadSessionPartCommand("etag-3", 1L)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldExpireUnfinishedSessionsAndDeleteTemporaryBlobs() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        session.setStatus(UploadSessionStatus.UPLOADING);
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

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoragePolicy createDefaultStoragePolicy() {
        StoragePolicy policy = new StoragePolicy();
        policy.setId(42L);
        policy.setName("Default S3 Compatible Storage");
        policy.setType(StoragePolicyType.S3_COMPATIBLE);
        policy.setEnabled(true);
        policy.setDefaultPolicy(true);
        return policy;
    }

    private UploadSession createSession(User user) {
        UploadSession session = new UploadSession();
        session.setSessionId("session-1");
        session.setUser(user);
        session.setTargetPath("/docs");
        session.setFilename("movie.mp4");
        session.setContentType("video/mp4");
        session.setSize(20L);
        session.setObjectKey("blobs/session-1");
        session.setMultipartUploadId(null);
        session.setChunkSize(8L * 1024 * 1024);
        session.setChunkCount(1);
        session.setUploadedPartsJson("[]");
        session.setStatus(UploadSessionStatus.CREATED);
        session.setCreatedAt(LocalDateTime.of(2026, 4, 8, 6, 0));
        session.setUpdatedAt(LocalDateTime.of(2026, 4, 8, 6, 0));
        session.setExpiresAt(LocalDateTime.of(2026, 4, 9, 6, 0));
        return session;
    }
}
