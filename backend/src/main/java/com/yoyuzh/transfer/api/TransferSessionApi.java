package com.yoyuzh.transfer.api;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TransferSessionApi {

    TransferSessionResponse createSession(Long senderUserId, CreateTransferSessionCommand command);

    LookupTransferSessionResponse lookupSession(String pickupCode);

    TransferSessionResponse joinSession(String sessionId);

    List<TransferSessionResponse> listOfflineSessions(Long senderUserId);

    void uploadOfflineFile(Long senderUserId, String sessionId, String fileId, MultipartFile multipartFile);

    void postSignal(String sessionId, String role, TransferSignalRequest request);

    PollTransferSignalsResponse pollSignals(String sessionId, String role, long after);

    OfflineDownloadResult downloadOfflineFile(String sessionId, String fileId);

    FileMetadataResponse importOfflineFile(Long recipientUserId, String sessionId, String fileId, TransferImportCommand command);

    void pruneExpiredTransfers();
}
