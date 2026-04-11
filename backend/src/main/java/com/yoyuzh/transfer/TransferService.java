package com.yoyuzh.transfer;

import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.core.FileMetadataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final OnlineTransferService onlineTransferService;
    private final OfflineTransferService offlineTransferService;
    private final TransferImportService transferImportService;
    private final AdminMetricsService adminMetricsService;

    @Transactional
    public TransferSessionResponse createSession(User sender, CreateTransferSessionRequest request) {
        pruneExpiredSessions();
        adminMetricsService.recordTransferUsage(request.files().stream().mapToLong(TransferFileItem::size).sum());
        if (request.mode() == TransferMode.OFFLINE) {
            if (sender == null) {
                throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "offline transfer requires authenticated user");
            }
            return offlineTransferService.createSession(sender, request);
        }
        return onlineTransferService.createSession(request);
    }

    public LookupTransferSessionResponse lookupSession(String pickupCode) {
        pruneExpiredSessions();
        String normalizedPickupCode = normalizePickupCode(pickupCode);

        LookupTransferSessionResponse online = onlineTransferService.lookupSession(normalizedPickupCode);
        if (online != null) {
            return online;
        }
        return offlineTransferService.lookupReadySession(normalizedPickupCode);
    }

    public TransferSessionResponse joinSession(String sessionId) {
        pruneExpiredSessions();
        TransferSessionResponse online = onlineTransferService.joinSession(sessionId);
        if (online != null) {
            return online;
        }
        return offlineTransferService.joinReadySession(sessionId);
    }

    public List<TransferSessionResponse> listOfflineSessions(User sender) {
        pruneExpiredSessions();
        return offlineTransferService.listOfflineSessions(sender);
    }

    @Transactional
    public void uploadOfflineFile(User sender, String sessionId, String fileId, MultipartFile multipartFile) {
        pruneExpiredSessions();
        offlineTransferService.uploadOfflineFile(sender, sessionId, fileId, multipartFile);
    }

    public void postSignal(String sessionId, String role, TransferSignalRequest request) {
        pruneExpiredSessions();
        if (onlineTransferService.postSignal(sessionId, role, request)) {
            return;
        }
        if (offlineTransferService.hasSession(sessionId)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline transfer does not need realtime signals");
        }
        throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "transfer session not found or expired");
    }

    public PollTransferSignalsResponse pollSignals(String sessionId, String role, long after) {
        pruneExpiredSessions();
        PollTransferSignalsResponse online = onlineTransferService.pollSignals(sessionId, role, after);
        if (online != null) {
            return online;
        }
        if (offlineTransferService.hasSession(sessionId)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline transfer does not need signal polling");
        }
        throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "transfer session not found or expired");
    }

    public ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId) {
        pruneExpiredSessions();
        adminMetricsService.recordDownloadTraffic(offlineTransferService.getReadyFileSize(sessionId, fileId));
        return offlineTransferService.downloadOfflineFile(sessionId, fileId);
    }

    @Transactional
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, String path) {
        pruneExpiredSessions();
        return transferImportService.importOfflineFile(recipient, sessionId, fileId, path);
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    @Transactional
    public void pruneExpiredTransfers() {
        pruneExpiredSessions();
    }

    private void pruneExpiredSessions() {
        Instant now = Instant.now();
        onlineTransferService.pruneExpiredSessions(now);
        offlineTransferService.pruneExpiredSessions(now);
    }

    private String normalizePickupCode(String pickupCode) {
        String normalized = Objects.requireNonNullElse(pickupCode, "").replaceAll("\\D", "");
        if (normalized.length() != 6) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid pickup code");
        }
        return normalized;
    }
}
