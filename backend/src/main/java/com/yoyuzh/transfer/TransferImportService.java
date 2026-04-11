package com.yoyuzh.transfer;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferImportService {

    private final OfflineTransferService offlineTransferService;
    private final FileService fileService;

    @Transactional
    public FileMetadataResponse importOfflineFile(User recipient, String sessionId, String fileId, String path) {
        OfflineTransferService.ReadyOfflineTransferFile readyFile = offlineTransferService.readReadyFile(sessionId, fileId);
        return fileService.importExternalFile(
                recipient,
                path,
                readyFile.filename(),
                readyFile.contentType(),
                readyFile.size(),
                readyFile.content()
        );
    }
}
