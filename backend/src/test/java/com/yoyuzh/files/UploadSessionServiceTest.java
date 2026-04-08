package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.config.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadSessionServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileService fileService;

    private UploadSessionService uploadSessionService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setMaxFileSize(500L * 1024 * 1024);
        uploadSessionService = new UploadSessionService(
                uploadSessionRepository,
                storedFileRepository,
                fileService,
                properties,
                Clock.fixed(Instant.parse("2026-04-08T06:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateUploadSessionWithoutChangingLegacyUploadPath() {
        User user = createUser(7L);
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "movie.mp4")).thenReturn(false);
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
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.CREATED);
        assertThat(session.getChunkSize()).isEqualTo(8L * 1024 * 1024);
        assertThat(session.getChunkCount()).isEqualTo(3);
        assertThat(session.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 4, 9, 6, 0));
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
        when(storedFileRepository.existsByUserIdAndPathAndFilename(7L, "/docs", "movie.mp4")).thenReturn(true);

        assertThatThrownBy(() -> uploadSessionService.createSession(
                user,
                new UploadSessionCreateCommand("/docs", "movie.mp4", "video/mp4", 20L)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCompleteOwnedSessionThroughLegacyFileCommitPath() {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        when(uploadSessionRepository.findBySessionIdAndUserId("session-1", 7L))
                .thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSession result = uploadSessionService.completeOwnedSession(user, "session-1");

        assertThat(result.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
        assertThat(result.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 6, 0));
        ArgumentCaptor<CompleteUploadRequest> requestCaptor = ArgumentCaptor.forClass(CompleteUploadRequest.class);
        verify(fileService).completeUpload(eq(user), requestCaptor.capture());
        assertThat(requestCaptor.getValue().path()).isEqualTo("/docs");
        assertThat(requestCaptor.getValue().filename()).isEqualTo("movie.mp4");
        assertThat(requestCaptor.getValue().storageName()).isEqualTo("blobs/session-1");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(requestCaptor.getValue().size()).isEqualTo(20L);
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

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        return user;
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
