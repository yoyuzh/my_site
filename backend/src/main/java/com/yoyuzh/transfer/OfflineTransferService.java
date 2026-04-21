package com.yoyuzh.transfer;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OfflineTransferService {

    private static final Duration OFFLINE_SESSION_TTL = Duration.ofDays(7);

    private final TransferSessionStore sessionStore;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final FileContentStorage fileContentStorage;
    private final OfflineTransferQuotaService offlineTransferQuotaService;
    private final long maxFileSize;

    public OfflineTransferService(TransferSessionStore sessionStore,
                                  OfflineTransferSessionRepository offlineTransferSessionRepository,
                                  FileContentStorage fileContentStorage,
                                  OfflineTransferQuotaService offlineTransferQuotaService,
                                  FileStorageProperties properties) {
        this.sessionStore = sessionStore;
        this.offlineTransferSessionRepository = offlineTransferSessionRepository;
        this.fileContentStorage = fileContentStorage;
        this.offlineTransferQuotaService = offlineTransferQuotaService;
        this.maxFileSize = properties.getMaxFileSize();
    }

    @Transactional
    public TransferSessionResponse createSession(Long senderUserId, CreateTransferSessionRequest request) {
        OfflineTransferSession session = new OfflineTransferSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setPickupCode(nextPickupCode());
        session.setSenderUserId(senderUserId);
        session.setExpiresAt(Instant.now().plus(OFFLINE_SESSION_TTL));
        session.setReady(false);

        for (TransferFileItem requestFile : request.files()) {
            OfflineTransferFile file = new OfflineTransferFile();
            String normalizedFilename = normalizeLeafName(requestFile.name());
            String normalizedRelativePath = normalizeRelativePath(requestFile.relativePath(), normalizedFilename);
            String fileId = UUID.randomUUID().toString();

            file.setId(fileId);
            file.setFilename(normalizedFilename);
            file.setRelativePath(normalizedRelativePath);
            file.setSize(requestFile.size());
            file.setContentType(normalizeContentType(requestFile.contentType()));
            file.setStorageName(buildTransferStorageName(fileId, normalizedFilename));
            file.setUploaded(false);
            session.addFile(file);
        }

        return toSessionResponse(offlineTransferSessionRepository.save(session));
    }

    public LookupTransferSessionResponse lookupReadySession(String pickupCode) {
        String normalizedPickupCode = normalizePickupCode(pickupCode);
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
                    normalizeContentType(targetFile.getContentType()),
                    multipartFile.getBytes()
            );
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline file upload failed");
        }

        targetFile.setUploaded(true);
        session.setReady(session.getFiles().stream().allMatch(OfflineTransferFile::isUploaded));
        offlineTransferSessionRepository.save(session);
    }

    public ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId) {
        OfflineTransferSession session = getRequiredOfflineReadySession(sessionId);
        OfflineTransferFile file = getRequiredOfflineFile(session, fileId);
        ensureOfflineFileUploaded(file);

        if (fileContentStorage.supportsDirectDownload()) {
            String downloadUrl = fileContentStorage.createTransferDownloadUrl(sessionId, file.getStorageName(), file.getFilename());
            return ResponseEntity.status(302).location(URI.create(downloadUrl)).build();
        }

        byte[] content = fileContentStorage.readTransferFile(sessionId, file.getStorageName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(file.getFilename(), StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(normalizeContentType(file.getContentType())))
                .body(content);
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
                normalizeContentType(file.getContentType()),
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
        String pickupCode;
        do {
            pickupCode = sessionStore.nextPickupCode();
        } while (offlineTransferSessionRepository.existsByPickupCode(pickupCode));
        return pickupCode;
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
                normalizeContentType(file.getContentType()),
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
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, notFoundMessage);
        }
        if (!session.isReady()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline transfer is still uploading");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = Objects.requireNonNullElse(contentType, "").trim();
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }

    private String normalizeLeafName(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "file name cannot be empty");
        }
        if (normalized.contains("/") || normalized.contains("\\") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid file name");
        }
        return normalized;
    }

    private String normalizeRelativePath(String relativePath, String fallbackFilename) {
        String rawPath = Objects.requireNonNullElse(relativePath, fallbackFilename).replace('\\', '/');
        List<String> segments = new ArrayList<>();
        for (String segment : rawPath.split("/")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (".".equals(trimmed) || "..".equals(trimmed)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "invalid file path");
            }
            segments.add(trimmed);
        }

        String normalizedFilename = normalizeLeafName(fallbackFilename);
        if (segments.isEmpty()) {
            return normalizedFilename;
        }

        List<String> normalizedSegments = new ArrayList<>(segments.subList(0, Math.max(0, segments.size() - 1)));
        normalizedSegments.add(normalizedFilename);
        return String.join("/", normalizedSegments);
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
