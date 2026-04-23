package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.LookupTransferSessionResponse;
import com.yoyuzh.transfer.api.OfflineDownloadResult;
import com.yoyuzh.transfer.api.PollTransferSignalsResponse;
import com.yoyuzh.transfer.api.TransferImportApi;
import com.yoyuzh.transfer.api.TransferImportCommand;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferMode;
import com.yoyuzh.transfer.api.TransferRuntimeMetricsPort;
import com.yoyuzh.transfer.api.TransferSessionApi;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import com.yoyuzh.transfer.api.TransferSignalRequest;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
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
    private final TransferRuntimeMetricsPort transferRuntimeMetricsPort;

    public RuntimeTransferSessionApi(OnlineTransferService onlineTransferService,
                                     OfflineTransferService offlineTransferService,
                                     TransferImportApi transferImportApi,
                                     TransferRuntimeMetricsPort transferRuntimeMetricsPort) {
        this.onlineTransferService = onlineTransferService;
        this.offlineTransferService = offlineTransferService;
        this.transferImportApi = transferImportApi;
        this.transferRuntimeMetricsPort = transferRuntimeMetricsPort;
    }

    @Override
    @Transactional
    public TransferSessionResponse createSession(Long senderUserId, CreateTransferSessionCommand command) {
        pruneExpiredSessions();
        transferRuntimeMetricsPort.recordTransferUsage(command.files().stream().mapToLong(TransferFileItem::size).sum());
        if (command.mode() == TransferMode.OFFLINE) {
            if (senderUserId == null) {
                throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "offline transfer requires authenticated user");
            }
            return offlineTransferService.createSession(senderUserId, command);
        }
        return onlineTransferService.createSession(command);
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
    public List<TransferSessionResponse> listOfflineSessions(Long senderUserId) {
        pruneExpiredSessions();
        return offlineTransferService.listOfflineSessions(senderUserId);
    }

    @Override
    @Transactional
    public void uploadOfflineFile(Long senderUserId, String sessionId, String fileId, MultipartFile multipartFile) {
        pruneExpiredSessions();
        offlineTransferService.uploadOfflineFile(senderUserId, sessionId, fileId, multipartFile);
    }

    @Override
    public void postSignal(String sessionId, String role, TransferSignalRequest request) {
        pruneExpiredSessions();
        if (onlineTransferService.postSignal(sessionId, role, request)) {
            return;
        }
        if (offlineTransferService.hasSession(sessionId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "offline transfer does not need realtime signals");
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
            throw new BusinessException(ErrorCode.INVALID_INPUT, "offline transfer does not need signal polling");
        }
        throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "transfer session not found or expired");
    }

    @Override
    public OfflineDownloadResult downloadOfflineFile(String sessionId, String fileId) {
        pruneExpiredSessions();
        transferRuntimeMetricsPort.recordDownloadTraffic(offlineTransferService.getReadyFileSize(sessionId, fileId));
        return offlineTransferService.downloadOfflineFile(sessionId, fileId);
    }

    @Override
    @Transactional
    public FileMetadataResponse importOfflineFile(Long recipientUserId, String sessionId, String fileId, TransferImportCommand command) {
        pruneExpiredSessions();
        return transferImportApi.importOfflineFile(recipientUserId, sessionId, fileId, command);
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
        return TransferPathNormalizer.normalizePickupCode(pickupCode);
    }
}
