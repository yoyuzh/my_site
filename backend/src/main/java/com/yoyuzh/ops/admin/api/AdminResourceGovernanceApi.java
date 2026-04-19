package com.yoyuzh.ops.admin.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface AdminResourceGovernanceApi {

    PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery);

    PageResponse<AdminFileBlobResponse> listFileBlobs(int page,
                                                      int size,
                                                      String userQuery,
                                                      Long storagePolicyId,
                                                      String objectKey,
                                                      AdminFileEntityType entityType);

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
