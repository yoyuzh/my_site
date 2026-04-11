package com.yoyuzh.admin;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminResourceGovernanceService {

    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final AdminAuditService adminAuditService;

    @Transactional
    public void deleteShare(Long shareId) {
        FileShareLink shareLink = fileShareLinkRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found"));
        fileShareLinkRepository.delete(shareLink);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("token", shareLink.getToken());
        adminAuditService.record(
                AdminAuditAction.DELETE_SHARE,
                "SHARE",
                shareId,
                "Deleted share link",
                details
        );
    }

    @Transactional
    public void deleteFile(Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        fileService.delete(storedFile.getUser(), fileId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ownerUserId", storedFile.getUser().getId());
        details.put("path", storedFile.getPath());
        details.put("filename", storedFile.getFilename());
        details.put("directory", storedFile.isDirectory());
        adminAuditService.record(
                AdminAuditAction.DELETE_FILE,
                "FILE",
                fileId,
                "Deleted file",
                details
        );
    }
}
