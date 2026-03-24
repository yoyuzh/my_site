package com.yoyuzh.transfer;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.FileMetadataResponse;
import com.yoyuzh.files.FileService;
import com.yoyuzh.files.storage.FileContentStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TransferService {

    private static final Duration ONLINE_SESSION_TTL = Duration.ofMinutes(15);
    private static final Duration OFFLINE_SESSION_TTL = Duration.ofDays(7);

    private final TransferSessionStore sessionStore;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final FileContentStorage fileContentStorage;
    private final FileService fileService;
    private final long maxFileSize;

    public TransferService(TransferSessionStore sessionStore,
                           OfflineTransferSessionRepository offlineTransferSessionRepository,
                           FileContentStorage fileContentStorage,
                           FileService fileService,
                           FileStorageProperties properties) {
        this.sessionStore = sessionStore;
        this.offlineTransferSessionRepository = offlineTransferSessionRepository;
        this.fileContentStorage = fileContentStorage;
        this.fileService = fileService;
        this.maxFileSize = properties.getMaxFileSize();
    }

    @Transactional
    public TransferSessionResponse createSession(User sender, CreateTransferSessionRequest request) {
        pruneExpiredSessions();
        if (request.mode() == TransferMode.OFFLINE) {
            return createOfflineSession(sender, request);
        }
        return createOnlineSession(request);
    }

    public LookupTransferSessionResponse lookupSession(String pickupCode) {
        pruneExpiredSessions();
        String normalizedPickupCode = normalizePickupCode(pickupCode);

        TransferSession onlineSession = sessionStore.findByPickupCode(normalizedPickupCode).orElse(null);
        if (onlineSession != null) {
            return onlineSession.toLookupResponse();
        }

        OfflineTransferSession offlineSession = getRequiredOfflineReadySessionByPickupCode(normalizedPickupCode);
        return toLookupResponse(offlineSession);
    }

    public TransferSessionResponse joinSession(String sessionId) {
        pruneExpiredSessions();

        TransferSession onlineSession = sessionStore.findById(sessionId).orElse(null);
        if (onlineSession != null) {
            try {
                onlineSession.markReceiverJoined();
            } catch (IllegalStateException ex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "在线快传不能被多次接收，请让发送方重新发起");
            }
            return onlineSession.toSessionResponse();
        }

        OfflineTransferSession offlineSession = getRequiredOfflineReadySession(sessionId);
        return toSessionResponse(offlineSession);
    }

    @Transactional
    public void uploadOfflineFile(User sender, String sessionId, String fileId, MultipartFile multipartFile) {
        pruneExpiredSessions();
        OfflineTransferSession session = getRequiredOfflineEditableSession(sender, sessionId);
        OfflineTransferFile targetFile = getRequiredOfflineFile(session, fileId);

        if (multipartFile.getSize() <= 0) {
            throw new BusinessException(ErrorCode.UNKNOWN, "离线文件不能为空");
        }
        if (multipartFile.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        if (multipartFile.getSize() != targetFile.getSize()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "离线文件大小与会话清单不一致");
        }

        try {
            fileContentStorage.storeTransferFile(
                    session.getSessionId(),
                    targetFile.getStorageName(),
                    normalizeContentType(targetFile.getContentType()),
                    multipartFile.getBytes()
            );
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "离线文件上传失败");
        }

        targetFile.setUploaded(true);
        session.setReady(session.getFiles().stream().allMatch(OfflineTransferFile::isUploaded));
        offlineTransferSessionRepository.save(session);
    }

    public void postSignal(String sessionId, String role, TransferSignalRequest request) {
        pruneExpiredSessions();
        TransferSession session = sessionStore.findById(sessionId).orElse(null);
        if (session == null) {
            if (offlineTransferSessionRepository.findWithFilesBySessionId(sessionId).isPresent()) {
                throw new BusinessException(ErrorCode.UNKNOWN, "离线快传无需建立在线连接");
            }
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "快传会话不存在或已失效");
        }
        session.enqueue(TransferRole.from(role), request.type().trim(), request.payload().trim());
    }

    public PollTransferSignalsResponse pollSignals(String sessionId, String role, long after) {
        pruneExpiredSessions();
        TransferSession session = sessionStore.findById(sessionId).orElse(null);
        if (session == null) {
            if (offlineTransferSessionRepository.findWithFilesBySessionId(sessionId).isPresent()) {
                throw new BusinessException(ErrorCode.UNKNOWN, "离线快传无需轮询信令");
            }
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "快传会话不存在或已失效");
        }
        return session.poll(TransferRole.from(role), Math.max(0, after));
    }

    public ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId) {
        pruneExpiredSessions();
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

    @Transactional
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, String path) {
        pruneExpiredSessions();
        OfflineTransferSession session = getRequiredOfflineReadySession(sessionId);
        OfflineTransferFile file = getRequiredOfflineFile(session, fileId);
        ensureOfflineFileUploaded(file);
        byte[] content = fileContentStorage.readTransferFile(sessionId, file.getStorageName());
        return fileService.importExternalFile(
                recipient,
                path,
                file.getFilename(),
                normalizeContentType(file.getContentType()),
                file.getSize(),
                content
        );
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    @Transactional
    public void pruneExpiredTransfers() {
        pruneExpiredSessions();
    }

    private TransferSessionResponse createOnlineSession(CreateTransferSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String pickupCode = nextPickupCode();
        Instant expiresAt = Instant.now().plus(ONLINE_SESSION_TTL);
        List<TransferFileItem> files = request.files().stream()
                .map(this::normalizeOnlineFileItem)
                .toList();

        TransferSession session = new TransferSession(sessionId, pickupCode, expiresAt, files);
        sessionStore.save(session);
        return session.toSessionResponse();
    }

    private TransferSessionResponse createOfflineSession(User sender, CreateTransferSessionRequest request) {
        OfflineTransferSession session = new OfflineTransferSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setPickupCode(nextPickupCode());
        session.setSenderUserId(sender.getId());
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

    private TransferFileItem normalizeOnlineFileItem(TransferFileItem file) {
        String normalizedFilename = normalizeLeafName(file.name());
        String normalizedRelativePath = normalizeRelativePath(file.relativePath(), normalizedFilename);
        return new TransferFileItem(
                null,
                normalizedFilename,
                normalizedRelativePath,
                file.size(),
                normalizeContentType(file.contentType()),
                null
        );
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

    private void pruneExpiredSessions() {
        Instant now = Instant.now();
        sessionStore.pruneExpired(now);
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

    private OfflineTransferSession getRequiredOfflineEditableSession(User sender, String sessionId) {
        OfflineTransferSession session = offlineTransferSessionRepository.findWithFilesBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "离线快传会话不存在或已失效"));
        if (!Objects.equals(session.getSenderUserId(), sender.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限上传该离线快传文件");
        }
        if (session.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "离线快传会话不存在或已失效");
        }
        return session;
    }

    private OfflineTransferSession getRequiredOfflineReadySession(String sessionId) {
        OfflineTransferSession session = offlineTransferSessionRepository.findWithFilesBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "离线快传会话不存在或已失效"));
        if (session.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "离线快传会话不存在或已失效");
        }
        if (!session.isReady()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "离线快传仍在上传中，请稍后再试");
        }
        return session;
    }

    private OfflineTransferSession getRequiredOfflineReadySessionByPickupCode(String pickupCode) {
        OfflineTransferSession session = offlineTransferSessionRepository.findWithFilesByPickupCode(pickupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "取件码不存在或已失效"));
        if (session.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "取件码不存在或已失效");
        }
        if (!session.isReady()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "离线快传仍在上传中，请稍后再试");
        }
        return session;
    }

    private OfflineTransferFile getRequiredOfflineFile(OfflineTransferSession session, String fileId) {
        return session.getFiles().stream()
                .filter(file -> file.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "离线文件不存在"));
    }

    private void ensureOfflineFileUploaded(OfflineTransferFile file) {
        if (!file.isUploaded()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "离线文件不存在");
        }
    }

    private String nextPickupCode() {
        String pickupCode;
        do {
            pickupCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        } while (sessionStore.findByPickupCode(pickupCode).isPresent()
                || offlineTransferSessionRepository.existsByPickupCode(pickupCode));
        return pickupCode;
    }

    private String normalizePickupCode(String pickupCode) {
        String normalized = Objects.requireNonNullElse(pickupCode, "").replaceAll("\\D", "");
        if (normalized.length() != 6) {
            throw new BusinessException(ErrorCode.UNKNOWN, "取件码格式不正确");
        }
        return normalized;
    }

    private String normalizeContentType(String contentType) {
        String normalized = Objects.requireNonNullElse(contentType, "").trim();
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }

    private String normalizeLeafName(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        if (normalized.contains("/") || normalized.contains("\\") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不合法");
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
                throw new BusinessException(ErrorCode.UNKNOWN, "文件路径不合法");
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
}
