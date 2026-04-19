package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminFileBlobResponse;
import com.yoyuzh.ops.admin.api.AdminFileEntityType;
import com.yoyuzh.ops.admin.api.AdminFileResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.ops.admin.api.AdminResourceGovernanceApi;
import com.yoyuzh.ops.admin.api.AdminShareResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminResourceController {

    private final AdminResourceGovernanceApi adminResourceGovernanceApi;

    @GetMapping("/files")
    public ApiResponse<PageResponse<AdminFileResponse>> files(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String query,
                                                              @RequestParam(defaultValue = "") String ownerQuery) {
        return ApiResponse.success(adminResourceGovernanceApi.listFiles(page, size, query, ownerQuery));
    }

    @GetMapping("/file-blobs")
    public ApiResponse<PageResponse<AdminFileBlobResponse>> fileBlobs(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "10") int size,
                                                                      @RequestParam(defaultValue = "") String userQuery,
                                                                      @RequestParam(required = false) Long storagePolicyId,
                                                                      @RequestParam(defaultValue = "") String objectKey,
                                                                      @RequestParam(required = false) AdminFileEntityType entityType) {
        return ApiResponse.success(
                adminResourceGovernanceApi.listFileBlobs(page, size, userQuery, storagePolicyId, objectKey, entityType)
        );
    }

    @GetMapping("/shares")
    public ApiResponse<PageResponse<AdminShareResponse>> shares(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size,
                                                                @RequestParam(defaultValue = "") String userQuery,
                                                                @RequestParam(defaultValue = "") String fileName,
                                                                @RequestParam(defaultValue = "") String token,
                                                                @RequestParam(required = false) Boolean passwordProtected,
                                                                @RequestParam(required = false) Boolean expired) {
        return ApiResponse.success(
                adminResourceGovernanceApi.listShares(page, size, userQuery, fileName, token, passwordProtected, expired)
        );
    }

    @DeleteMapping("/shares/{shareId}")
    public ApiResponse<Void> deleteShare(@PathVariable Long shareId) {
        adminResourceGovernanceApi.deleteShare(shareId);
        return ApiResponse.success();
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
        adminResourceGovernanceApi.deleteFile(fileId);
        return ApiResponse.success();
    }
}
