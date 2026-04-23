package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.LookupTransferSessionResponse;
import com.yoyuzh.transfer.api.OfflineDownloadResult;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferMode;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import com.yoyuzh.transfer.internal.domain.OfflineTransferFile;
import com.yoyuzh.transfer.internal.domain.OfflineTransferSession;
import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import com.yoyuzh.transfer.internal.infra.TransferSessionStore;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OfflineTransferService {

    private static final Duration OFFLINE_SESSION_TTL = Duration.ofDays(7);
    private static final int PICKUP_CODE_COLLISION_RETRY_LIMIT = 32;

    private final TransferSessionStore sessionStore;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final FileContentStorage fileContentStorage;
    private final OfflineTransferQuotaService offlineTransferQuotaService;
    private final long maxFileSize;

    public OfflineTransferService(TransferSessionStore sessionStore,
                                  OfflineTransferSessionRepository offlineTransferSessionRepository,
                                  FileContentStorage fileContentStorage,
                                  OfflineTransferQuotaService offlineTransferQuotaService,
                                  StorageRuntimeProperties storageRuntimeProperties) {
        this.sessionStore = sessionStore;
        this.offlineTransferSessionRepository = offlineTransferSessionRepository;
        this.fileContentStorage = fileContentStorage;
        this.offlineTransferQuotaService = offlineTransferQuotaService;
        this.maxFileSize = storageRuntimeProperties.getMaxFileSize();
    }

    @Transactional
    public TransferSessionResponse createSession(Long senderUserId, CreateTransferSessionCommand command) {
        OfflineTransferSession session = new OfflineTransferSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setPickupCode(nextPickupCode());
        session.setSenderUserId(senderUserId);
        session.setExpiresAt(Instant.now().plus(OFFLINE_SESSION_TTL));
        session.setReady(false);

        for (TransferFileItem requestFile : command.files()) {
            OfflineTransferFile file = new OfflineTransferFile();
            String normalizedFilename = TransferPathNormalizer.normalizeLeafName(requestFile.name());
            String normalizedRelativePath = TransferPathNormalizer.normalizeRelativePath(requestFile.relativePath(), normalizedFilename);
            String fileId = UUID.randomUUID().toString();

            file.setId(fileId);
            file.setFilename(normalizedFilename);
            file.setRelativePath(normalizedRelativePath);
            file.setSize(requestFile.size());
            file.setContentType(TransferPathNormalizer.normalizeContentType(requestFile.contentType()));
            file.setStorageName(buildTransferStorageName(fileId, normalizedFilename));
            file.setUploaded(false);
            session.addFile(file);
        }

        return toSessionResponse(offlineTransferSessionRepository.save(session));
    }

    public LookupTransferSessionResponse lookupReadySession(String pickupCode) {
        String normalizedPickupCode = TransferPathNormalizer.normalizePickupCode(pickupCode);
        OfflineTransferSession offlineSession = offlineTransferSessionRepository.findWithFilesByPickupCode(normalizedPickupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "pickup code not found or expired"));
        validateOfflineReadySession(offlineSession, "pickup code not found or expired");
        return toLookupResponse(offlineSession);
    }

    public TransferSessionResponse joinReadySession(String sessionId) {
        OfflineTransferSession offlineSession = offlineTransferSessionRepository.findWithFilesBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "offline transfer session not found or expired"));
        validateOfflineReadySession(offlineSession, "offline transfer session not found or expired");
        return toSessionResponse(offlineSession);
    }

    public List<TransferSessionResponse> listOfflineSessions(Long senderUserId) {
        return offlineTransferSessionRepository.findActiveWithFilesBySenderUserId(senderUserId, Instant.now()).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public boolean hasSession(String sessionId) {
        return offlineTransferSessionRepository.findWithFilesBySessionId(sessionId).isPresent();
    }

    @Transactional
    public void uploadOfflineFile(Long senderUserId, String sessionId, String fileId, MultipartFile multipartFile) {
        OfflineTransferSession session = getRequiredOfflineEditableSession(senderUserId, sessionId);
        OfflineTransferFile targetFile = getRequiredOfflineFile(session, fileId);
        offlineTransferQuotaService.ensureUploadAllowed(targetFile, multipartFile.getSize(), maxFileSize);

        try {
            fileContentStorage.storeTransferFile(
                    session.getSessionId(),
                    targetFile.getStorageName(),
                    TransferPathNormalizer.normalizeContentType(targetFile.getContentType()),
                    multipartFile.getBytes()
            );
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline file upload failed");
        }

        targetFile.setUploaded(true);
        session.setReady(session.getFiles().stream().allMatch(OfflineTransferFile::isUploaded));
        offlineTransferSessionRepository.save(session);
    }

    public OfflineDownloadResult downloadOfflineFile(String sessionId, String fileId) {
        OfflineTransferSession session = getRequiredOfflineReadySession(sessionId);
        OfflineTransferFile file = getRequiredOfflineFile(session, fileId);
        ensureOfflineFileUploaded(file);

        if (fileContentStorage.supportsDirectDownload()) {
            String downloadUrl = fileContentStorage.createTransferDownloadUrl(sessionId, file.getStorageName(), file.getFilename());
            return OfflineDownloadResult.redirect(downloadUrl);
        }

        byte[] content = fileContentStorage.readTransferFile(sessionId, file.getStorageName());
        return OfflineDownloadResult.inline(
                file.getFilename(),
                TransferPathNormalizer.normalizeContentType(file.getContentType()),
                content
        );
    }

    public long getReadyFileSize(String sessionId, String fileId) {
        OfflineTransferSession session = getRequiredOfflineReadySession(sessionId);
        OfflineTransferFile file = getRequiredOfflineFile(session, fileId);
        ensureOfflineFileUploaded(file);
        return file.getSize();
    }

    public ReadyOfflineTransferFile readReadyFile(String sessionId, String fileId) {
        OfflineTransferSession session = getRequiredOfflineReadySession(sessionId);
        OfflineTransferFile file = getRequiredOfflineFile(session, fileId);
        ensureOfflineFileUploaded(file);
        byte[] content = fileContentStorage.readTransferFile(sessionId, file.getStorageName());
        return new ReadyOfflineTransferFile(
                file.getFilename(),
                TransferPathNormalizer.normalizeContentType(file.getContentType()),
                file.getSize(),
                content
        );
    }

    @Transactional
    public void pruneExpiredSessions(Instant now) {
        List<OfflineTransferSession> expiredSessions = offlineTransferSessionRepository.findAllExpiredWithFiles(now);
        if (expiredSessions.isEmpty()) {
            return;
        }

        for (OfflineTransferSession session : expiredSessions) {
            for (OfflineTransferFile file : session.getFiles()) {
                if (file.isUploaded()) {
                    fileContentStorage.deleteTransferFile(session.getSessionId(), file.getStorageName());
                }
            }
        }
        offlineTransferSessionRepository.deleteAll(expiredSessions);
    }

    private String nextPickupCode() {
        for (int attempt = 0; attempt < PICKUP_CODE_COLLISION_RETRY_LIMIT; attempt++) {
            String pickupCode = sessionStore.nextPickupCode();
            if (!offlineTransferSessionRepository.existsByPickupCode(pickupCode)) {
                return pickupCode;
            }
        }
        throw new BusinessException(ErrorCode.UNKNOWN, "unable to allocate pickup code");
    }

    private TransferSessionResponse toSessionResponse(OfflineTransferSession session) {
        return new TransferSessionResponse(
                session.getSessionId(),
                session.getPickupCode(),
                TransferMode.OFFLINE,
                session.getExpiresAt(),
                session.getFiles().stream().map(this::toFileItem).toList()
        );
    }

    private LookupTransferSessionResponse toLookupResponse(OfflineTransferSession session) {
        return new LookupTransferSessionResponse(
                session.getSessionId(),
                session.getPickupCode(),
                TransferMode.OFFLINE,
                session.getExpiresAt()
        );
    }

    private TransferFileItem toFileItem(OfflineTransferFile file) {
        return new TransferFileItem(
                file.getId(),
                file.getFilename(),
                file.getRelativePath(),
                file.getSize(),
                TransferPathNormalizer.normalizeContentType(file.getContentType()),
                file.isUploaded()
        );
    }

    private OfflineTransferSession getRequiredOfflineEditableSession(Long senderUserId, String sessionId) {
        OfflineTransferSession session = offlineTransferSessionRepository.findWithFilesBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "offline transfer session not found or expired"));
        if (!Objects.equals(session.getSenderUserId(), senderUserId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "no permission to upload this offline transfer file");
        }
        if (session.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "offline transfer session not found or expired");
        }
        return session;
    }

    private OfflineTransferSession getRequiredOfflineReadySession(String sessionId) {
        OfflineTransferSession session = offlineTransferSessionRepository.findWithFilesBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "offline transfer session not found or expired"));
        validateOfflineReadySession(session, "offline transfer session not found or expired");
        return session;
    }

    private OfflineTransferFile getRequiredOfflineFile(OfflineTransferSession session, String fileId) {
        return session.getFiles().stream()
                .filter(file -> file.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "offline transfer file not found"));
    }

    private void ensureOfflineFileUploaded(OfflineTransferFile file) {
        if (!file.isUploaded()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "offline transfer file not found");
        }
    }

    private String normalizePickupCode(String pickupCode) {
        String normalized = Objects.requireNonNullElse(pickupCode, "").replaceAll("\\D", "");
        if (normalized.length() != 6) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid pickup code");
        }
        return normalized;
    }

    private void validateOfflineReadySession(OfflineTransferSession session, String notFoundMessage) {
        if (session.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED, notFoundMessage);
        }
        if (!session.isReady()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "offline transfer is still uploading");
        }
    }

    private String buildTransferStorageName(String fileId, String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        String extension = extensionIndex > 0 ? filename.substring(extensionIndex) : "";
        return fileId + extension;
    }

    public record ReadyOfflineTransferFile(
            String filename,
            String contentType,
            long size,
            byte[] content
    ) {
    }
}
