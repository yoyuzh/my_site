package com.yoyuzh.ops.admin.api;

import com.yoyuzh.admin.AdminFileBlobResponse;
import com.yoyuzh.admin.AdminFileResponse;
import com.yoyuzh.admin.AdminShareResponse;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileEntityType;

public interface AdminResourceGovernanceApi {

    PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery);

    PageResponse<AdminFileBlobResponse> listFileBlobs(int page,
                                                      int size,
                                                      String userQuery,
                                                      Long storagePolicyId,
                                                      String objectKey,
                                                      FileEntityType entityType);

    PageResponse<AdminShareResponse> listShares(int page,
                                                int size,
                                                String userQuery,
                                                String fileName,
                                                String token,
                                                Boolean passwordProtected,
                                                Boolean expired);

    void deleteShare(Long shareId);

    void deleteFile(Long fileId);
}
