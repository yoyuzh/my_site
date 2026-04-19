package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.admin.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.transfer.CreateTransferSessionRequest;
import com.yoyuzh.transfer.LookupTransferSessionResponse;
import com.yoyuzh.transfer.OfflineTransferService;
import com.yoyuzh.transfer.OnlineTransferService;
import com.yoyuzh.transfer.PollTransferSignalsResponse;
import com.yoyuzh.transfer.TransferFileItem;
import com.yoyuzh.transfer.TransferMode;
import com.yoyuzh.transfer.TransferSessionResponse;
import com.yoyuzh.transfer.TransferSignalRequest;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferImportApi;
import com.yoyuzh.transfer.api.TransferImportCommand;
import com.yoyuzh.transfer.api.TransferSessionApi;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class RuntimeTransferSessionApi implements TransferSessionApi {

    private final OnlineTransferService onlineTransferService;
    private final OfflineTransferService offlineTransferService;
    private final TransferImportApi transferImportApi;
    private final AdminMetricsService adminMetricsService;

    public RuntimeTransferSessionApi(OnlineTransferService onlineTransferService,
                                     OfflineTransferService offlineTransferService,
                                     TransferImportApi transferImportApi,
                                     AdminMetricsService adminMetricsService) {
        this.onlineTransferService = onlineTransferService;
        this.offlineTransferService = offlineTransferService;
        this.transferImportApi = transferImportApi;
        this.adminMetricsService = adminMetricsService;
    }

    @Override
    @Transactional
    public TransferSessionResponse createSession(User sender, CreateTransferSessionCommand command) {
        pruneExpiredSessions();
        adminMetricsService.recordTransferUsage(command.files().stream().mapToLong(TransferFileItem::size).sum());
        if (command.mode() == TransferMode.OFFLINE) {
            if (sender == null) {
                throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "offline transfer requires authenticated user");
            }
            return offlineTransferService.createSession(sender, new CreateTransferSessionRequest(command.mode(), command.files()));
        }
        return onlineTransferService.createSession(new CreateTransferSessionRequest(command.mode(), command.files()));
    }

    @Override
    public LookupTransferSessionResponse lookupSession(String pickupCode) {
        pruneExpiredSessions();
        String normalizedPickupCode = normalizePickupCode(pickupCode);

        LookupTransferSessionResponse online = onlineTransferService.lookupSession(normalizedPickupCode);
        if (online != null) {
            return online;
        }
        return offlineTransferService.lookupReadySession(normalizedPickupCode);
    }

    @Override
    public TransferSessionResponse joinSession(String sessionId) {
        pruneExpiredSessions();
        TransferSessionResponse online = onlineTransferService.joinSession(sessionId);
        if (online != null) {
            return online;
        }
        return offlineTransferService.joinReadySession(sessionId);
    }

    @Override
    public List<TransferSessionResponse> listOfflineSessions(User sender) {
        pruneExpiredSessions();
        return offlineTransferService.listOfflineSessions(sender);
    }

    @Override
    @Transactional
    public void uploadOfflineFile(User sender, String sessionId, String fileId, MultipartFile multipartFile) {
        pruneExpiredSessions();
        offlineTransferService.uploadOfflineFile(sender, sessionId, fileId, multipartFile);
    }

    @Override
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

    @Override
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

    @Override
    public ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId) {
        pruneExpiredSessions();
        adminMetricsService.recordDownloadTraffic(offlineTransferService.getReadyFileSize(sessionId, fileId));
        return offlineTransferService.downloadOfflineFile(sessionId, fileId);
    }

    @Override
    @Transactional
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, TransferImportCommand command) {
        pruneExpiredSessions();
        return transferImportApi.importOfflineFile(recipient, sessionId, fileId, command);
    }

    @Override
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
