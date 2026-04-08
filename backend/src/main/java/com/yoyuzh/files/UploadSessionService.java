package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.FileStorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UploadSessionService {

    private static final long DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024;
    private static final long SESSION_TTL_HOURS = 24;

    private final UploadSessionRepository uploadSessionRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;
    private final long maxFileSize;
    private final Clock clock;

    @Autowired
    public UploadSessionService(UploadSessionRepository uploadSessionRepository,
                                StoredFileRepository storedFileRepository,
                                FileService fileService,
                                FileStorageProperties properties) {
        this(uploadSessionRepository, storedFileRepository, fileService, properties, Clock.systemUTC());
    }

    UploadSessionService(UploadSessionRepository uploadSessionRepository,
                         StoredFileRepository storedFileRepository,
                         FileService fileService,
                         FileStorageProperties properties,
                         Clock clock) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileService = fileService;
        this.maxFileSize = properties.getMaxFileSize();
        this.clock = clock;
    }

    @Transactional
    public UploadSession createSession(User user, UploadSessionCreateCommand command) {
        String normalizedPath = normalizeDirectoryPath(command.path());
        String filename = normalizeLeafName(command.filename());
        validateTarget(user, normalizedPath, filename, command.size());

        UploadSession session = new UploadSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setTargetPath(normalizedPath);
        session.setFilename(filename);
        session.setContentType(command.contentType());
        session.setSize(command.size());
        session.setObjectKey(createBlobObjectKey());
        session.setChunkSize(DEFAULT_CHUNK_SIZE);
        session.setChunkCount(calculateChunkCount(command.size(), DEFAULT_CHUNK_SIZE));
        session.setUploadedPartsJson("[]");
        session.setStatus(UploadSessionStatus.CREATED);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plusHours(SESSION_TTL_HOURS));
        return uploadSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public UploadSession getOwnedSession(User user, String sessionId) {
        return uploadSessionRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "上传会话不存在"));
    }

    @Transactional
    public UploadSession cancelOwnedSession(User user, String sessionId) {
        UploadSession session = getOwnedSession(user, sessionId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.UNKNOWN, "已完成的上传会话不能取消");
        }
        session.setStatus(UploadSessionStatus.CANCELLED);
        session.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
        return uploadSessionRepository.save(session);
    }

    @Transactional
    public UploadSession completeOwnedSession(User user, String sessionId) {
        UploadSession session = getOwnedSession(user, sessionId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            return session;
        }
        if (session.getStatus() == UploadSessionStatus.CANCELLED || session.getStatus() == UploadSessionStatus.FAILED) {
            throw new BusinessException(ErrorCode.UNKNOWN, "上传会话不能完成");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        if (session.getExpiresAt().isBefore(now)) {
            session.setStatus(UploadSessionStatus.EXPIRED);
            session.setUpdatedAt(now);
            uploadSessionRepository.save(session);
            throw new BusinessException(ErrorCode.UNKNOWN, "上传会话已过期");
        }

        session.setStatus(UploadSessionStatus.COMPLETING);
        session.setUpdatedAt(now);
        uploadSessionRepository.save(session);

        try {
            fileService.completeUpload(user, new CompleteUploadRequest(
                    session.getTargetPath(),
                    session.getFilename(),
                    session.getObjectKey(),
                    session.getContentType(),
                    session.getSize()
            ));
            session.setStatus(UploadSessionStatus.COMPLETED);
            session.setUpdatedAt(now);
            return uploadSessionRepository.save(session);
        } catch (RuntimeException ex) {
            session.setStatus(UploadSessionStatus.FAILED);
            session.setUpdatedAt(now);
            uploadSessionRepository.save(session);
            throw ex;
        }
    }

    private void validateTarget(User user, String normalizedPath, String filename, long size) {
        long effectiveMaxUploadSize = Math.min(maxFileSize, user.getMaxUploadSizeBytes());
        if (size > effectiveMaxUploadSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedPath, filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
        }
        long usedBytes = storedFileRepository.sumFileSizeByUserId(user.getId());
        if (user.getStorageQuotaBytes() >= 0 && usedBytes + size > user.getStorageQuotaBytes()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "存储空间不足");
        }
    }

    private int calculateChunkCount(long size, long chunkSize) {
        if (size <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) size / chunkSize);
    }

    private String createBlobObjectKey() {
        return "blobs/" + UUID.randomUUID();
    }

    private String normalizeDirectoryPath(String path) {
        String cleaned = StringUtils.cleanPath(path == null ? "/" : path.trim().replace("\\", "/"));
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }
        while (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "路径不合法");
        }
        return cleaned;
    }

    private String normalizeLeafName(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "" : filename).trim();
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        if (cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不合法");
        }
        return cleaned;
    }
}
