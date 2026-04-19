package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.api.AdminFileEntityType;
import com.yoyuzh.ops.admin.api.AdminFileBlobResponse;
import com.yoyuzh.ops.admin.api.AdminFileResponse;
import com.yoyuzh.ops.admin.api.AdminResourceGovernanceApi;
import com.yoyuzh.ops.admin.api.AdminShareResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.stereotype.Service;

@Service
public class RuntimeAdminResourceGovernanceApi implements AdminResourceGovernanceApi {

    private final AdminInspectionQueryService adminInspectionQueryService;
    private final AdminResourceGovernanceService adminResourceGovernanceService;

    public RuntimeAdminResourceGovernanceApi(AdminInspectionQueryService adminInspectionQueryService,
                                             AdminResourceGovernanceService adminResourceGovernanceService) {
        this.adminInspectionQueryService = adminInspectionQueryService;
        this.adminResourceGovernanceService = adminResourceGovernanceService;
    }

    @Override
    public PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery) {
        return adminInspectionQueryService.listFiles(page, size, query, ownerQuery);
    }

    @Override
    public PageResponse<AdminFileBlobResponse> listFileBlobs(int page,
                                                             int size,
                                                             String userQuery,
                                                             Long storagePolicyId,
                                                             String objectKey,
                                                             AdminFileEntityType entityType) {
        return adminInspectionQueryService.listFileBlobs(page, size, userQuery, storagePolicyId, objectKey, entityType);
    }

    @Override
    public PageResponse<AdminShareResponse> listShares(int page,
                                                       int size,
                                                       String userQuery,
                                                       String fileName,
                                                       String token,
                                                       Boolean passwordProtected,
                                                       Boolean expired) {
        return adminInspectionQueryService.listShares(page, size, userQuery, fileName, token, passwordProtected, expired);
    }

    @Override
    public void deleteShare(Long shareId) {
        adminResourceGovernanceService.deleteShare(shareId);
    }

    @Override
    public void deleteFile(Long fileId) {
        adminResourceGovernanceService.deleteFile(fileId);
    }
}
