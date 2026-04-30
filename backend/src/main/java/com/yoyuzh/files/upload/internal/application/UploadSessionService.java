package com.yoyuzh.files.upload.internal.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.content.api.MultipartCompletedPart;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.UploadSessionTransportPolicy;
import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.upload.internal.domain.UploadSession;
import com.yoyuzh.files.upload.internal.domain.UploadSessionRepository;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStateMachine;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStatus;
import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadSessionService {

    private static final long DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024;
    private static final long SESSION_TTL_HOURS = 24;
    private static final List<UploadSessionStatus> EXPIRABLE_STATUSES = List.of(
            UploadSessionStatus.CREATED,
            UploadSessionStatus.UPLOADING,
            UploadSessionStatus.COMPLETING
    );
    private static final TypeReference<List<UploadedPart>> UPLOADED_PARTS_TYPE = new TypeReference<>() {
    };

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadTargetPolicy uploadTargetPolicy;
    private final UploadCompletionApi uploadCompletionApi;
    private final FileContentStorage fileContentStorage;
    private final UploadSessionTransportPolicy uploadSessionTransportPolicy;
    private final ObjectMapper objectMapper;
    private final UploadPolicyResolver uploadPolicyResolver;
    private final UploadSessionStateMachine uploadSessionStateMachine;
    private final Clock clock;
    private final UploadSessionRuntimeStateService uploadSessionRuntimeStateService;
    private final UploadSessionTusService uploadSessionTusService;

    @Autowired
    public UploadSessionService(UploadSessionRepository uploadSessionRepository,
                                UploadTargetPolicy uploadTargetPolicy,
                                UploadCompletionApi uploadCompletionApi,
                                FileContentStorage fileContentStorage,
                                UploadSessionTransportPolicy uploadSessionTransportPolicy,
                                ObjectMapper objectMapper,
                                UploadPolicyResolver uploadPolicyResolver,
                                UploadSessionStateMachine uploadSessionStateMachine,
                                ObjectProvider<UploadSessionRuntimeStateService> uploadSessionRuntimeStateServiceProvider,
                                UploadSessionTusService uploadSessionTusService) {
        this(
                uploadSessionRepository,
                uploadTargetPolicy,
                uploadCompletionApi,
                fileContentStorage,
                uploadSessionTransportPolicy,
                Clock.systemUTC(),
                objectMapper,
                uploadPolicyResolver,
                uploadSessionStateMachine,
                uploadSessionRuntimeStateServiceProvider.getIfAvailable(UploadSessionRuntimeStateService::noOp),
                uploadSessionTusService
        );
    }

    UploadSessionService(UploadSessionRepository uploadSessionRepository,
                         UploadTargetPolicy uploadTargetPolicy,
                         UploadCompletionApi uploadCompletionApi,
                         FileContentStorage fileContentStorage,
                         UploadSessionTransportPolicy uploadSessionTransportPolicy,
                         Clock clock,
                         ObjectMapper objectMapper,
                         UploadPolicyResolver uploadPolicyResolver,
                         UploadSessionStateMachine uploadSessionStateMachine,
                         UploadSessionRuntimeStateService uploadSessionRuntimeStateService,
                         UploadSessionTusService uploadSessionTusService) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadTargetPolicy = uploadTargetPolicy;
        this.uploadCompletionApi = uploadCompletionApi;
        this.fileContentStorage = fileContentStorage;
        this.uploadSessionTransportPolicy = uploadSessionTransportPolicy;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.uploadPolicyResolver = uploadPolicyResolver;
        this.uploadSessionStateMachine = uploadSessionStateMachine;
        this.uploadSessionRuntimeStateService = uploadSessionRuntimeStateService;
        this.uploadSessionTusService = uploadSessionTusService;
    }

    @Transactional
    public UploadSessionView createSession(IdentityAuthenticatedUser user, UploadSessionCreateCommand command) {
        ValidatedUploadTarget target = uploadTargetPolicy.validateUpload(
                user.id(),
                user.maxUploadSizeBytes(),
                user.storageQuotaBytes(),
                command.path(),
                command.filename(),
                command.size()
        );
        return toView(createSessionEntity(user.id(), target, command));
    }

    private UploadSession createSessionEntity(Long userId, ValidatedUploadTarget target, UploadSessionCreateCommand command) {
        var defaultPolicySnapshot = target.defaultPolicySnapshot();
        StoragePolicyCapabilities capabilities = defaultPolicySnapshot.capabilities();
        UploadSessionUploadMode uploadMode = uploadPolicyResolver.resolveUploadMode(capabilities);

        UploadSession session = new UploadSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTargetPath(target.normalizedPath());
        session.setFilename(target.filename());
        session.setContentType(command.contentType());
        session.setSize(command.size());
        session.setObjectKey(createBlobObjectKey());
        session.setStoragePolicyId(defaultPolicySnapshot.policyId());
        session.setChunkSize(DEFAULT_CHUNK_SIZE);
        session.setChunkCount(uploadMode == UploadSessionUploadMode.DIRECT_MULTIPART
                ? uploadPolicyResolver.calculateChunkCount(command.size(), DEFAULT_CHUNK_SIZE)
                : 1);
        session.setUploadedPartsJson("[]");
        LocalDateTime now = now();
        session.initializeCreated(now, now.plusHours(SESSION_TTL_HOURS));
        if (uploadMode == UploadSessionUploadMode.DIRECT_MULTIPART) {
            session.setMultipartUploadId(fileContentStorage.createMultipartUpload(session.getObjectKey(), session.getContentType()));
        }
        UploadSession savedSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markCreated(savedSession);
        return savedSession;
    }

    @Transactional(readOnly = true)
    public UploadSessionView getOwnedSession(Long userId, String sessionId) {
        return toView(getOwnedSessionEntity(userId, sessionId));
    }

    @Transactional(readOnly = true)
    UploadSession getOwnedSessionEntity(Long userId, String sessionId) {
        return uploadSessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "upload session not found"));
    }

    @Transactional(readOnly = true)
    public Optional<UploadSessionRuntimeState> getRuntimeState(String sessionId) {
        return uploadSessionRuntimeStateService.getState(sessionId);
    }

    @Transactional
    public UploadSessionView cancelOwnedSession(Long userId, String sessionId) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        return toView(cancelSession(session));
    }

    private UploadSession cancelSession(UploadSession session) {
        uploadSessionStateMachine.ensureCancellable(session);
        uploadSessionStateMachine.markCancelled(session, now());
        UploadSession savedSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markCancelled(savedSession, savedSession.getUpdatedAt());
        return savedSession;
    }

    @Transactional(readOnly = true)
    public PreparedUpload prepareOwnedUpload(Long userId, String sessionId) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        return prepareUpload(session);
    }

    private PreparedUpload prepareUpload(UploadSession session) {
        ensureSessionCanReceiveContent(session, now());
        if (resolveUploadMode(session) != UploadSessionUploadMode.DIRECT_SINGLE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session does not support direct single upload");
        }
        return fileContentStorage.prepareBlobUpload(
                session.getTargetPath(),
                session.getFilename(),
                session.getObjectKey(),
                session.getContentType(),
                session.getSize()
        );
    }

    @Transactional(readOnly = true)
    public PreparedUpload prepareOwnedPartUpload(Long userId, String sessionId, int partIndex) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        return preparePartUpload(session, partIndex);
    }

    private PreparedUpload preparePartUpload(UploadSession session, int partIndex) {
        ensureSessionCanReceivePart(session, now());
        if (resolveUploadMode(session) != UploadSessionUploadMode.DIRECT_MULTIPART
                || !StringUtils.hasText(session.getMultipartUploadId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session does not support multipart upload");
        }
        if (partIndex < 0 || partIndex >= session.getChunkCount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid part index");
        }
        return fileContentStorage.prepareMultipartPartUpload(
                session.getObjectKey(),
                session.getMultipartUploadId(),
                partIndex + 1,
                session.getContentType(),
                uploadPolicyResolver.resolveChunkSize(session, partIndex)
        );
    }

    @Transactional
    public UploadSessionView recordUploadedPart(Long userId,
                                                String sessionId,
                                                int partIndex,
                                                UploadSessionPartCommand command) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        return toView(recordUploadedPartEntity(session, partIndex, command));
    }

    private UploadSession recordUploadedPartEntity(UploadSession session,
                                                   int partIndex,
                                                   UploadSessionPartCommand command) {
        LocalDateTime now = now();
        ensureSessionCanReceivePart(session, now);
        if (resolveUploadMode(session) != UploadSessionUploadMode.DIRECT_MULTIPART) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session does not support multipart upload");
        }
        if (partIndex < 0 || partIndex >= session.getChunkCount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid part index");
        }
        if (!StringUtils.hasText(command.etag())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "part etag is required");
        }
        if (command.size() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid part size");
        }

        List<UploadedPart> uploadedParts = new ArrayList<>(readUploadedParts(session));
        uploadedParts.removeIf(part -> part.partIndex() == partIndex);
        uploadedParts.add(new UploadedPart(partIndex, command.etag(), command.size(), now.toString()));
        uploadedParts.sort(Comparator.comparingInt(UploadedPart::partIndex));

        session.setUploadedPartsJson(writeUploadedParts(uploadedParts));
        uploadSessionStateMachine.markUploading(session, now);
        UploadSession savedSession = uploadSessionRepository.save(session);
        long uploadedBytes = uploadedParts.stream().mapToLong(UploadedPart::size).sum();
        uploadSessionRuntimeStateService.markUploading(
                savedSession,
                uploadedBytes,
                uploadedParts.size(),
                savedSession.getUpdatedAt()
        );
        return savedSession;
    }

    @Transactional
    public UploadSessionView uploadOwnedContent(Long userId, String sessionId, MultipartFile file) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        return toView(uploadContent(session, file));
    }

    private UploadSession uploadContent(UploadSession session, MultipartFile file) {
        LocalDateTime now = now();
        ensureSessionCanReceiveContent(session, now);
        if (resolveUploadMode(session) != UploadSessionUploadMode.PROXY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session does not support proxy upload");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload content is required");
        }
        if (file.getSize() != session.getSize()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload size does not match session");
        }
        fileContentStorage.uploadBlob(session.getObjectKey(), file);
        uploadSessionStateMachine.markUploading(session, now);
        UploadSession savedSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markUploading(
                savedSession,
                savedSession.getSize(),
                1,
                savedSession.getUpdatedAt()
        );
        return savedSession;
    }

    @Transactional
    public UploadSessionView completeOwnedSession(Long userId, String sessionId) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        return toView(completeSession(session, userId));
    }

    @Transactional
    public UploadSessionTusState startTusSession(Long userId, String sessionId, Long uploadLength) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        long offset = uploadSessionTusService.start(session, uploadLength);
        return new UploadSessionTusState(offset, session.getSize() == null ? 0L : session.getSize());
    }

    @Transactional(readOnly = true)
    public UploadSessionTusState getTusSessionState(Long userId, String sessionId) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        long offset = uploadSessionTusService.currentOffset(session);
        return new UploadSessionTusState(offset, session.getSize() == null ? 0L : session.getSize());
    }

    @Transactional
    public UploadSessionTusState appendTusSession(Long userId,
                                                  String sessionId,
                                                  long uploadOffset,
                                                  Path stagedContent,
                                                  long contentLength) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        try (InputStream content = Files.newInputStream(stagedContent)) {
            long nextOffset = uploadSessionTusService.append(session, uploadOffset, content, contentLength);
            return new UploadSessionTusState(nextOffset, session.getSize() == null ? 0L : session.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "failed to read staged tus patch content");
        }
    }

    @Transactional
    public void cancelTusSession(Long userId, String sessionId) {
        UploadSession session = getOwnedSessionEntity(userId, sessionId);
        uploadSessionTusService.delete(session);
        cancelSession(session);
    }

    private UploadSession completeSession(UploadSession session, Long userId) {
        LocalDateTime now = now();
        try {
            uploadSessionStateMachine.ensureCompletable(session, now);
        } catch (BusinessException ex) {
            if (session.getStatus() == UploadSessionStatus.EXPIRED) {
                UploadSession expiredSession = uploadSessionRepository.save(session);
                uploadSessionRuntimeStateService.markExpired(expiredSession, expiredSession.getUpdatedAt());
            }
            throw ex;
        }
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            return session;
        }

        uploadSessionStateMachine.markCompleting(session, now);
        UploadSession completingSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markUploading(
                completingSession,
                completingSession.getSize() == null ? 0L : completingSession.getSize(),
                Math.max(1, completingSession.getChunkCount() == null ? 1 : completingSession.getChunkCount()),
                completingSession.getUpdatedAt()
        );

        try {
            if (usesTusUpload(session)) {
                uploadSessionTusService.finalizeUpload(session);
            } else if (resolveUploadMode(session) == UploadSessionUploadMode.DIRECT_MULTIPART
                    && StringUtils.hasText(session.getMultipartUploadId())) {
                fileContentStorage.completeMultipartUpload(
                        session.getObjectKey(),
                        session.getMultipartUploadId(),
                        toCompletedParts(session)
                );
            }
            uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                    userId,
                    session.getTargetPath(),
                    session.getFilename(),
                    session.getObjectKey(),
                    session.getContentType(),
                    session.getSize()
            ));
            uploadSessionStateMachine.markCompleted(session, now);
            UploadSession completedSession = uploadSessionRepository.save(session);
            uploadSessionRuntimeStateService.markCompleted(completedSession, completedSession.getUpdatedAt());
            return completedSession;
        } catch (RuntimeException ex) {
            uploadSessionStateMachine.markFailed(session, now);
            UploadSession failedSession = uploadSessionRepository.save(session);
            uploadSessionRuntimeStateService.markFailed(failedSession, failedSession.getUpdatedAt());
            throw ex;
        }
    }

    @Transactional
    public int pruneExpiredSessions() {
        LocalDateTime now = now();
        List<UploadSession> expiredSessions = uploadSessionRepository.findByStatusInAndExpiresAtBefore(
                EXPIRABLE_STATUSES,
                now
        );
        for (UploadSession session : expiredSessions) {
            try {
                if (usesTusUpload(session)) {
                    uploadSessionTusService.delete(session);
                } else if (StringUtils.hasText(session.getMultipartUploadId())) {
                    fileContentStorage.abortMultipartUpload(session.getObjectKey(), session.getMultipartUploadId());
                } else {
                    fileContentStorage.deleteBlob(session.getObjectKey());
                }
            } catch (RuntimeException ignored) {
                // Expiration is authoritative in the database even if remote object cleanup fails.
            }
            uploadSessionStateMachine.markExpired(session, now);
            uploadSessionRuntimeStateService.markExpired(session, session.getUpdatedAt());
        }
        if (!expiredSessions.isEmpty()) {
            uploadSessionRepository.saveAll(expiredSessions);
        }
        return expiredSessions.size();
    }

    private UploadSessionUploadMode resolveUploadMode(UploadSession session) {
        return uploadSessionTransportPolicy.resolveUploadMode(
                session.getStoragePolicyId(),
                session.getMultipartUploadId(),
                session.getChunkCount()
        );
    }

    private boolean usesTusUpload(UploadSession session) {
        return session != null && uploadSessionTransportPolicy.usesTusUpload(session.getStoragePolicyId());
    }

    private void ensureSessionCanReceiveContent(UploadSession session, LocalDateTime now) {
        try {
            uploadSessionStateMachine.ensureCanReceiveContent(
                    session,
                    now,
                    StringUtils.hasText(session.getMultipartUploadId())
            );
        } catch (BusinessException ex) {
            markRuntimeExpiredIfNeeded(session);
            throw ex;
        }
    }

    private void ensureSessionCanReceivePart(UploadSession session, LocalDateTime now) {
        try {
            uploadSessionStateMachine.ensureCanReceivePart(session, now);
        } catch (BusinessException ex) {
            markRuntimeExpiredIfNeeded(session);
            throw ex;
        }
    }

    private void markRuntimeExpiredIfNeeded(UploadSession session) {
        if (session.getStatus() != UploadSessionStatus.EXPIRED) {
            return;
        }
        UploadSession expiredSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markExpired(expiredSession, expiredSession.getUpdatedAt());
    }

    private List<UploadedPart> readUploadedParts(UploadSession session) {
        if (!StringUtils.hasText(session.getUploadedPartsJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(session.getUploadedPartsJson(), UPLOADED_PARTS_TYPE);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid uploaded part state");
        }
    }

    private String writeUploadedParts(List<UploadedPart> uploadedParts) {
        try {
            return objectMapper.writeValueAsString(uploadedParts);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "failed to write uploaded part state");
        }
    }

    private List<MultipartCompletedPart> toCompletedParts(UploadSession session) {
        List<UploadedPart> uploadedParts = readUploadedParts(session).stream()
                .sorted(Comparator.comparingInt(UploadedPart::partIndex))
                .toList();
        if (uploadedParts.size() != session.getChunkCount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "multipart upload is incomplete");
        }
        for (int expectedIndex = 0; expectedIndex < session.getChunkCount(); expectedIndex++) {
            UploadedPart part = uploadedParts.get(expectedIndex);
            if (part.partIndex() != expectedIndex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "multipart upload is incomplete");
            }
            if (!StringUtils.hasText(part.etag())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "missing part etag");
            }
            if (part.size() <= 0 || part.size() > uploadPolicyResolver.resolveChunkSize(session, expectedIndex)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid part size");
            }
        }
        return uploadedParts.stream()
                .map(part -> new MultipartCompletedPart(part.partIndex() + 1, part.etag()))
                .toList();
    }

    private record UploadedPart(int partIndex, String etag, long size, String uploadedAt) {
    }

    private UploadSessionView toView(UploadSession session) {
        UploadSessionUploadMode uploadMode = resolveUploadMode(session);
        return new UploadSessionView(
                session.getSessionId(),
                session.getObjectKey(),
                session.getTargetPath(),
                session.getFilename(),
                session.getContentType(),
                session.getSize(),
                session.getStoragePolicyId(),
                session.getStatus(),
                session.getChunkSize(),
                session.getChunkCount(),
                session.getExpiresAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                uploadSessionRuntimeStateService.getState(session.getSessionId()).orElse(null),
                uploadMode,
                usesTusUpload(session)
        );
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

}
