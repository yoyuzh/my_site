package com.yoyuzh.transfer.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.transfer.LookupTransferSessionResponse;
import com.yoyuzh.transfer.PollTransferSignalsResponse;
import com.yoyuzh.transfer.TransferSessionResponse;
import com.yoyuzh.transfer.TransferSignalRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TransferSessionApi {

    TransferSessionResponse createSession(User sender, CreateTransferSessionCommand command);

    LookupTransferSessionResponse lookupSession(String pickupCode);

    TransferSessionResponse joinSession(String sessionId);

    List<TransferSessionResponse> listOfflineSessions(User sender);

    void uploadOfflineFile(User sender, String sessionId, String fileId, MultipartFile multipartFile);

    void postSignal(String sessionId, String role, TransferSignalRequest request);

    PollTransferSignalsResponse pollSignals(String sessionId, String role, long after);

    ResponseEntity<?> downloadOfflineFile(String sessionId, String fileId);

    FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, TransferImportCommand command);

    void pruneExpiredTransfers();
}
