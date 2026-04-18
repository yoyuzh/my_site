package com.yoyuzh.admin;

import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileEntityType;
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

    private final AdminInspectionQueryService adminInspectionQueryService;
    private final AdminResourceGovernanceService adminResourceGovernanceService;

    @GetMapping("/files")
    public ApiResponse<PageResponse<AdminFileResponse>> files(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String query,
                                                              @RequestParam(defaultValue = "") String ownerQuery) {
        return ApiResponse.success(adminInspectionQueryService.listFiles(page, size, query, ownerQuery));
    }

    @GetMapping("/file-blobs")
    public ApiResponse<PageResponse<AdminFileBlobResponse>> fileBlobs(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "10") int size,
                                                                      @RequestParam(defaultValue = "") String userQuery,
                                                                      @RequestParam(required = false) Long storagePolicyId,
                                                                      @RequestParam(defaultValue = "") String objectKey,
                                                                      @RequestParam(required = false) FileEntityType entityType) {
        return ApiResponse.success(
                adminInspectionQueryService.listFileBlobs(page, size, userQuery, storagePolicyId, objectKey, entityType)
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
                adminInspectionQueryService.listShares(page, size, userQuery, fileName, token, passwordProtected, expired)
        );
    }

    @DeleteMapping("/shares/{shareId}")
    public ApiResponse<Void> deleteShare(@PathVariable Long shareId) {
        adminResourceGovernanceService.deleteShare(shareId);
        return ApiResponse.success();
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
        adminResourceGovernanceService.deleteFile(fileId);
        return ApiResponse.success();
    }
}
