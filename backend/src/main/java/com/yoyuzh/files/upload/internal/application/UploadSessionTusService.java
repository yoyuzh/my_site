package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.upload.internal.domain.UploadSession;
import com.yoyuzh.files.upload.internal.domain.UploadSessionRepository;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStateMachine;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyType;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class UploadSessionTusService {

    private static final String TUS_RESUMABLE = "1.0.0";
    private static final Duration SESSION_LOCK_TTL = Duration.ofMinutes(5);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadSessionStateMachine uploadSessionStateMachine;
    private final UploadSessionRuntimeStateService uploadSessionRuntimeStateService;
    private final FileContentStorage fileContentStorage;
    private final Clock clock;
    private final Path tusTempRoot;
    private final DistributedLockGateway distributedLockGateway;

    @Autowired
    public UploadSessionTusService(UploadSessionRepository uploadSessionRepository,
                                   UploadSessionStateMachine uploadSessionStateMachine,
                                   ObjectProvider<UploadSessionRuntimeStateService> uploadSessionRuntimeStateServiceProvider,
                                   FileContentStorage fileContentStorage,
                                   ObjectProvider<DistributedLockGateway> distributedLockGatewayProvider) {
        this(uploadSessionRepository,
                uploadSessionStateMachine,
                uploadSessionRuntimeStateServiceProvider.getIfAvailable(UploadSessionRuntimeStateService::noOp),
                fileContentStorage,
                distributedLockGatewayProvider.getIfAvailable(DistributedLockGateway::noOp),
                Clock.systemUTC(),
                Path.of(System.getProperty("java.io.tmpdir"), "yoyuzh-portal", "tus"));
    }

    UploadSessionTusService(UploadSessionRepository uploadSessionRepository,
                            UploadSessionStateMachine uploadSessionStateMachine,
                            UploadSessionRuntimeStateService uploadSessionRuntimeStateService,
                            FileContentStorage fileContentStorage,
                            DistributedLockGateway distributedLockGateway,
                            Clock clock,
                            Path tusTempRoot) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadSessionStateMachine = uploadSessionStateMachine;
        this.uploadSessionRuntimeStateService = uploadSessionRuntimeStateService;
        this.fileContentStorage = fileContentStorage;
        this.distributedLockGateway = distributedLockGateway == null
                ? DistributedLockGateway.noOp()
                : distributedLockGateway;
        this.clock = clock;
        this.tusTempRoot = tusTempRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.tusTempRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize tus temp storage", ex);
        }
    }

    public boolean supportsTus(StoragePolicyDescriptor descriptor) {
        return descriptor != null
                && (descriptor.type() == StoragePolicyType.LOCAL || descriptor.type() == StoragePolicyType.WEBDAV);
    }

    public String tusResumableVersion() {
        return TUS_RESUMABLE;
    }

    public long start(UploadSession session, Long uploadLength) {
        return executeWithSessionLock(session, () -> {
            if (uploadLength == null || !uploadLength.equals(session.getSize())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "upload length does not match session");
            }
            Path file = tempFile(session);
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) {
                    Files.createFile(file);
                }
            } catch (IOException ex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "Failed to initialize tus upload");
            }
            return readCurrentOffset(file);
        });
    }

    public long currentOffset(UploadSession session) {
        return readCurrentOffset(tempFile(session));
    }

    public long append(UploadSession session, long expectedOffset, InputStream content, long contentLength) {
        return executeWithSessionLock(session, () -> appendLocked(session, expectedOffset, content, contentLength));
    }

    public void finalizeUpload(UploadSession session) {
        executeWithSessionLock(session, () -> {
            Path file = tempFile(session);
            long currentOffset = readCurrentOffset(file);
            if (currentOffset != session.getSize()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session is not fully uploaded");
            }
            boolean stored = false;
            try (InputStream inputStream = Files.newInputStream(file, StandardOpenOption.READ)) {
                fileContentStorage.storeBlob(session.getObjectKey(), session.getContentType(), inputStream, session.getSize());
                stored = true;
            } catch (IOException ex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "Failed to finalize tus upload");
            } finally {
                if (stored) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                }
            }
            return null;
        });
    }

    public void delete(UploadSession session) {
        executeWithSessionLock(session, () -> {
            try {
                Files.deleteIfExists(tempFile(session));
                Files.deleteIfExists(chunkTempFile(session));
            } catch (IOException ignored) {
            }
            return null;
        });
    }

    private int resolveUploadedChunkCount(UploadSession session, long uploadedBytes) {
        long chunkSize = session.getChunkSize() == null || session.getChunkSize() <= 0 ? uploadedBytes : session.getChunkSize();
        if (chunkSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) uploadedBytes / chunkSize);
    }

    private Path tempFile(UploadSession session) {
        return safeResolve(session.getSessionId() + ".bin");
    }

    private Path chunkTempFile(UploadSession session) {
        return safeResolve(session.getSessionId() + ".chunk");
    }

    private Path safeResolve(String fileName) {
        Path resolved = tusTempRoot.resolve(fileName).toAbsolutePath().normalize();
        if (!resolved.startsWith(tusTempRoot)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid tus upload path");
        }
        return resolved;
    }

    private long appendLocked(UploadSession session, long expectedOffset, InputStream content, long contentLength) {
        LocalDateTime now = LocalDateTime.now(clock);
        uploadSessionStateMachine.ensureCanReceiveContent(session, now, false);
        Path file = tempFile(session);
        long currentOffset = readCurrentOffset(file);
        if (currentOffset != expectedOffset) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload offset does not match session");
        }
        long remainingBytes = session.getSize() - currentOffset;
        if (remainingBytes < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session already exceeds declared size");
        }
        if (contentLength > remainingBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload chunk exceeds declared session size");
        }
        Path chunkFile = chunkTempFile(session);
        try (InputStream inputStream = content) {
            Files.createDirectories(file.getParent());
            long copied = writeChunkFile(inputStream, chunkFile, remainingBytes);
            if (contentLength >= 0 && copied != contentLength) {
                throw new BusinessException(ErrorCode.UNKNOWN, "uploaded bytes do not match content length");
            }
            appendChunkFile(file, chunkFile);
            long updatedOffset = currentOffset + copied;
            uploadSessionStateMachine.markUploading(session, now);
            UploadSession savedSession = uploadSessionRepository.save(session);
            uploadSessionRuntimeStateService.markUploading(
                    savedSession,
                    updatedOffset,
                    resolveUploadedChunkCount(savedSession, updatedOffset),
                    savedSession.getUpdatedAt()
            );
            return updatedOffset;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Failed to append tus upload");
        } finally {
            try {
                Files.deleteIfExists(chunkFile);
            } catch (IOException ignored) {
            }
        }
    }

    private long readCurrentOffset(Path file) {
        try {
            return Files.exists(file) ? Files.size(file) : 0L;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Failed to read tus upload state");
        }
    }

    private long writeChunkFile(InputStream inputStream, Path chunkFile, long remainingBytes) throws IOException {
        long copied = 0L;
        try (OutputStream outputStream = Files.newOutputStream(
                chunkFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (copied + read > remainingBytes) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "upload chunk exceeds declared session size");
                }
                outputStream.write(buffer, 0, read);
                copied += read;
            }
        }
        return copied;
    }

    private void appendChunkFile(Path file, Path chunkFile) throws IOException {
        try (InputStream chunkInputStream = Files.newInputStream(chunkFile, StandardOpenOption.READ);
             OutputStream outputStream = Files.newOutputStream(
                     file,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND
             )) {
            chunkInputStream.transferTo(outputStream);
        }
    }

    private <T> T executeWithSessionLock(UploadSession session, Supplier<T> action) {
        return distributedLockGateway.executeWithLock(
                "upload-session-tus:" + session.getSessionId(),
                SESSION_LOCK_TTL,
                action
        );
    }
}
