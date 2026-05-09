package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.sharing.api.SharingAdminShareSnapshot;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminResourceGovernanceService {

    private final WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;
    private final SharingApi sharingApi;
    private final AdminAuditService adminAuditService;

    @Transactional
    public void deleteShare(Long shareId) {
        SharingAdminShareSnapshot shareSnapshot = sharingApi.deleteShareAsAdmin(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND, "share not found"));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("token", shareSnapshot.token());
        adminAuditService.record(
                AdminAuditAction.SHARE_DELETED,
                "SHARE",
                shareId,
                "Deleted share link",
                details
        );
    }

    @Transactional
    public void deleteFile(Long fileId) {
        WorkspaceAdminFileSnapshot fileSnapshot = workspaceAdminGovernanceApi.deleteFileAsAdmin(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ownerUserId", fileSnapshot.ownerUserId());
        details.put("path", fileSnapshot.path());
        details.put("filename", fileSnapshot.filename());
        details.put("directory", fileSnapshot.directory());
        adminAuditService.record(
                AdminAuditAction.FILE_DELETED,
                "FILE",
                fileId,
                "Deleted file",
                details
        );
    }
}
