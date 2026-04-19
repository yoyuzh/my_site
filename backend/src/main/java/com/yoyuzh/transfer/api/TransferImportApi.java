package com.yoyuzh.transfer.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;

public interface TransferImportApi {

    FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, TransferImportCommand command);
}
