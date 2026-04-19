package com.yoyuzh.files.upload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.MultipartCompletedPart;
import com.yoyuzh.files.storage.PreparedUpload;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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
    private final StoragePolicyService storagePolicyService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UploadPolicyResolver uploadPolicyResolver;
    private final UploadSessionStateMachine uploadSessionStateMachine;
    private final Clock clock;
    @Autowired(required = false)
    private UploadSessionRuntimeStateService uploadSessionRuntimeStateService = UploadSessionRuntimeStateService.noOp();

    @Autowired
    public UploadSessionService(UploadSessionRepository uploadSessionRepository,
                                UploadTargetPolicy uploadTargetPolicy,
                                UploadCompletionApi uploadCompletionApi,
                                FileContentStorage fileContentStorage,
                                StoragePolicyService storagePolicyService,
                                UploadPolicyResolver uploadPolicyResolver,
                                UploadSessionStateMachine uploadSessionStateMachine) {
        this(
                uploadSessionRepository,
                uploadTargetPolicy,
                uploadCompletionApi,
                fileContentStorage,
                storagePolicyService,
                Clock.systemUTC(),
                uploadPolicyResolver,
                uploadSessionStateMachine
        );
    }

    UploadSessionService(UploadSessionRepository uploadSessionRepository,
                         UploadTargetPolicy uploadTargetPolicy,
                         UploadCompletionApi uploadCompletionApi,
                         FileContentStorage fileContentStorage,
                         StoragePolicyService storagePolicyService,
                         Clock clock) {
        this(
                uploadSessionRepository,
                uploadTargetPolicy,
                uploadCompletionApi,
                fileContentStorage,
                storagePolicyService,
                clock,
                new UploadPolicyResolver(),
                new UploadSessionStateMachine()
        );
    }

    UploadSessionService(UploadSessionRepository uploadSessionRepository,
                         UploadTargetPolicy uploadTargetPolicy,
                         UploadCompletionApi uploadCompletionApi,
                         FileContentStorage fileContentStorage,
                         StoragePolicyService storagePolicyService,
                         Clock clock,
                         UploadPolicyResolver uploadPolicyResolver,
                         UploadSessionStateMachine uploadSessionStateMachine) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadTargetPolicy = uploadTargetPolicy;
        this.uploadCompletionApi = uploadCompletionApi;
        this.fileContentStorage = fileContentStorage;
        this.storagePolicyService = storagePolicyService;
        this.clock = clock;
        this.uploadPolicyResolver = uploadPolicyResolver;
        this.uploadSessionStateMachine = uploadSessionStateMachine;
    }

    @Transactional
    public UploadSession createSession(User user, UploadSessionCreateCommand command) {
        ValidatedUploadTarget target = uploadTargetPolicy.validateUpload(user, command.path(), command.filename(), command.size());
        var defaultPolicySnapshot = target.defaultPolicySnapshot();
        StoragePolicy policy = defaultPolicySnapshot.policy();
        StoragePolicyCapabilities capabilities = defaultPolicySnapshot.capabilities();
        UploadSessionUploadMode uploadMode = uploadPolicyResolver.resolveUploadMode(capabilities);

        UploadSession session = new UploadSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setTargetPath(target.normalizedPath());
        session.setFilename(target.filename());
        session.setContentType(command.contentType());
        session.setSize(command.size());
        session.setObjectKey(createBlobObjectKey());
        session.setStoragePolicyId(policy.getId());
        session.setChunkSize(DEFAULT_CHUNK_SIZE);
        session.setChunkCount(uploadMode == UploadSessionUploadMode.DIRECT_MULTIPART
                ? uploadPolicyResolver.calculateChunkCount(command.size(), DEFAULT_CHUNK_SIZE)
                : 1);
        session.setUploadedPartsJson("[]");
        session.setStatus(UploadSessionStatus.CREATED);
        LocalDateTime now = now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plusHours(SESSION_TTL_HOURS));
        if (uploadMode == UploadSessionUploadMode.DIRECT_MULTIPART) {
            session.setMultipartUploadId(fileContentStorage.createMultipartUpload(session.getObjectKey(), session.getContentType()));
        }
        UploadSession savedSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markCreated(savedSession);
        return savedSession;
    }

    @Transactional(readOnly = true)
    public UploadSession getOwnedSession(User user, String sessionId) {
        return uploadSessionRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "upload session not found"));
    }

    @Transactional(readOnly = true)
    public Optional<UploadSessionRuntimeState> getRuntimeState(String sessionId) {
        return uploadSessionRuntimeStateService.getState(sessionId);
    }

    @Transactional
    public UploadSession cancelOwnedSession(User user, String sessionId) {
        UploadSession session = getOwnedSession(user, sessionId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.UNKNOWN, "completed upload session cannot be cancelled");
        }
        uploadSessionStateMachine.markCancelled(session, now());
        UploadSession savedSession = uploadSessionRepository.save(session);
        uploadSessionRuntimeStateService.markCancelled(savedSession, savedSession.getUpdatedAt());
        return savedSession;
    }

    @Transactional(readOnly = true)
    public PreparedUpload prepareOwnedUpload(User user, String sessionId) {
        UploadSession session = getOwnedSession(user, sessionId);
        ensureSessionCanReceiveContent(session, now());
        if (resolveUploadMode(session) != UploadSessionUploadMode.DIRECT_SINGLE) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload session does not support direct single upload");
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
    public PreparedUpload prepareOwnedPartUpload(User user, String sessionId, int partIndex) {
        UploadSession session = getOwnedSession(user, sessionId);
        ensureSessionCanReceivePart(session, now());
        if (resolveUploadMode(session) != UploadSessionUploadMode.DIRECT_MULTIPART
                || !StringUtils.hasText(session.getMultipartUploadId())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload session does not support multipart upload");
        }
        if (partIndex < 0 || partIndex >= session.getChunkCount()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid part index");
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
    public UploadSession recordUploadedPart(User user,
                                            String sessionId,
                                            int partIndex,
                                            UploadSessionPartCommand command) {
        UploadSession session = getOwnedSession(user, sessionId);
        LocalDateTime now = now();
        ensureSessionCanReceivePart(session, now);
        if (resolveUploadMode(session) != UploadSessionUploadMode.DIRECT_MULTIPART) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload session does not support multipart upload");
        }
        if (partIndex < 0 || partIndex >= session.getChunkCount()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid part index");
        }
        if (!StringUtils.hasText(command.etag())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "part etag is required");
        }
        if (command.size() < 0) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid part size");
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
    public UploadSession uploadOwnedContent(User user, String sessionId, MultipartFile file) {
        UploadSession session = getOwnedSession(user, sessionId);
        LocalDateTime now = now();
        ensureSessionCanReceiveContent(session, now);
        if (resolveUploadMode(session) != UploadSessionUploadMode.PROXY) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload session does not support proxy upload");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload content is required");
        }
        if (file.getSize() != session.getSize()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload size does not match session");
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
    public UploadSession completeOwnedSession(User user, String sessionId) {
        UploadSession session = getOwnedSession(user, sessionId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            return session;
        }
        if (session.getStatus() == UploadSessionStatus.CANCELLED || session.getStatus() == UploadSessionStatus.FAILED) {
            throw new BusinessException(ErrorCode.UNKNOWN, "upload session cannot be completed");
        }
        LocalDateTime now = now();
        if (session.getExpiresAt().isBefore(now)) {
            uploadSessionStateMachine.markExpired(session, now);
            UploadSession expiredSession = uploadSessionRepository.save(session);
            uploadSessionRuntimeStateService.markExpired(expiredSession, expiredSession.getUpdatedAt());
            throw new BusinessException(ErrorCode.UNKNOWN, "upload session has expired");
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
            if (resolveUploadMode(session) == UploadSessionUploadMode.DIRECT_MULTIPART
                    && StringUtils.hasText(session.getMultipartUploadId())) {
                fileContentStorage.completeMultipartUpload(
                        session.getObjectKey(),
                        session.getMultipartUploadId(),
                        toCompletedParts(session)
                );
            }
            uploadCompletionApi.completeStoredBlob(new UploadCompletionCommand(
                    user,
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

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    @Transactional
    public int pruneExpiredSessions() {
        LocalDateTime now = now();
        List<UploadSession> expiredSessions = uploadSessionRepository.findByStatusInAndExpiresAtBefore(
                EXPIRABLE_STATUSES,
                now
        );
        for (UploadSession session : expiredSessions) {
            try {
                if (StringUtils.hasText(session.getMultipartUploadId())) {
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

    public UploadSessionUploadMode resolveUploadMode(UploadSession session) {
        if (session.getStoragePolicyId() == null) {
            if (StringUtils.hasText(session.getMultipartUploadId()) || session.getChunkCount() > 1) {
                return UploadSessionUploadMode.DIRECT_MULTIPART;
            }
            return UploadSessionUploadMode.PROXY;
        }
        StoragePolicy policy = storagePolicyService.getRequiredPolicy(session.getStoragePolicyId());
        return storagePolicyService.resolveUploadMode(storagePolicyService.readCapabilities(policy));
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
            throw new BusinessException(ErrorCode.UNKNOWN, "multipart upload is incomplete");
        }
        for (int expectedIndex = 0; expectedIndex < session.getChunkCount(); expectedIndex++) {
            UploadedPart part = uploadedParts.get(expectedIndex);
            if (part.partIndex() != expectedIndex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "multipart upload is incomplete");
            }
            if (!StringUtils.hasText(part.etag())) {
                throw new BusinessException(ErrorCode.UNKNOWN, "missing part etag");
            }
            if (part.size() <= 0 || part.size() > uploadPolicyResolver.resolveChunkSize(session, expectedIndex)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "invalid part size");
            }
        }
        return uploadedParts.stream()
                .map(part -> new MultipartCompletedPart(part.partIndex() + 1, part.etag()))
                .toList();
    }

    private record UploadedPart(int partIndex, String etag, long size, String uploadedAt) {
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

}
