package com.yoyuzh.transfer;

import com.yoyuzh.ops.admin.internal.application.AdminMetricsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferImportCommand;
import com.yoyuzh.transfer.api.TransferSessionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferSessionApi transferSessionApi;

    @Transactional
    public TransferSessionResponse createSession(User sender, CreateTransferSessionRequest request) {
        return transferSessionApi.createSession(sender, new CreateTransferSessionCommand(request.mode(), request.files()));
    }

    public LookupTransferSessionResponse lookupSession(String pickupCode) {
        return transferSessionApi.lookupSession(pickupCode);
    }

    public TransferSessionResponse joinSession(String sessionId) {
        return transferSessionApi.joinSession(sessionId);
    }

    public List<TransferSessionResponse> listOfflineSessions(User sender) {
        return transferSessionApi.listOfflineSessions(sender);
    }

    @Transactional
    public void uploadOfflineFile(User sender, String sessionId, String fileId, MultipartFile multipartFile) {
        transferSessionApi.uploadOfflineFile(sender, sessionId, fileId, multipartFile);
    }

    public void postSignal(String sessionId, String role, TransferSignalRequest request) {
        transferSessionApi.postSignal(sessionId, role, request);
    }

    public PollTransferSignalsResponse pollSignals(String sessionId, String role, long after) {
        return transferSessionApi.pollSignals(sessionId, role, after);
    }

    public ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId) {
        return transferSessionApi.downloadOfflineFile(sessionId, fileId);
    }

    @Transactional
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, String path) {
        return transferSessionApi.importOfflineFile(recipient, sessionId, fileId, new TransferImportCommand(path));
    }
}
