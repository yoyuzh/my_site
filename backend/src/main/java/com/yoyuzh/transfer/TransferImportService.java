package com.yoyuzh.transfer;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.transfer.api.TransferImportApi;
import com.yoyuzh.transfer.api.TransferImportCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferImportService {

    private final TransferImportApi transferImportApi;

    @Transactional
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, String path) {
        return transferImportApi.importOfflineFile(recipient, sessionId, fileId, new TransferImportCommand(path));
    }
}
