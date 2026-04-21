package com.yoyuzh.transfer.api;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;

public interface TransferImportApi {

    FileMetadataResponse importOfflineFile(Long recipientUserId, String sessionId, String fileId, TransferImportCommand command);
}
