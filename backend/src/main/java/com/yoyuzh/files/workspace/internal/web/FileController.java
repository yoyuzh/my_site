package com.yoyuzh.files.workspace.internal.web;

import com.yoyuzh.boot.security.AuthenticatedUserPrincipal;
import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.upload.CompleteUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadRequest;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.workspace.api.BatchFileOperationRequest;
import com.yoyuzh.files.workspace.api.DownloadUrlResponse;
import com.yoyuzh.files.workspace.api.FavoriteFileResponse;
import com.yoyuzh.files.workspace.api.FileViewerConfigResponse;
import com.yoyuzh.files.workspace.api.FileDeleteMode;
import com.yoyuzh.files.workspace.api.FileDetailResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.RecycleBinItemResponse;
import com.yoyuzh.files.workspace.api.WorkspaceTagResponse;
import com.yoyuzh.files.workspace.api.WorkspaceArchiveListing;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.application.FileViewerConfigService;
import com.yoyuzh.files.workspace.internal.application.WorkspaceRequestProbe;
import com.yoyuzh.files.workspace.internal.application.WorkspaceTagService;
import com.yoyuzh.files.workspace.internal.application.WorkspaceViewerTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final CustomUserDetailsService userDetailsService;
    private final WorkspaceTagService workspaceTagService;
    private final FileViewerConfigService fileViewerConfigService;
    private final WorkspaceRequestProbe workspaceRequestProbe;
    private final WorkspaceViewerTokenService workspaceViewerTokenService;

    @Operation(summary = "获取文件打开方式配置")
    @GetMapping("/viewers/config")
    public ApiResponse<FileViewerConfigResponse> viewerConfig() {
        return ApiResponse.success(fileViewerConfigService.defaultConfig());
    }

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<FileMetadataResponse> upload(@AuthenticationPrincipal UserDetails principal,
                                                    @RequestParam(defaultValue = "/") String path,
                                                    @RequestPart("file") MultipartFile file) {
        return workspaceRequestProbe.trace(
                "files.upload",
                java.util.Map.of(
                        "path", path,
                        "filename", file == null ? "" : String.valueOf(file.getOriginalFilename()),
                        "size", file == null ? 0L : file.getSize()
                ),
                () -> ApiResponse.success(fileService.upload(currentUser(principal), path, file))
        );
    }

    @Operation(summary = "初始化上传")
    @PostMapping("/upload/initiate")
    public ApiResponse<InitiateUploadResponse> initiateUpload(@AuthenticationPrincipal UserDetails principal,
                                                              @Valid @RequestBody InitiateUploadRequest request) {
        return workspaceRequestProbe.trace(
                "files.upload.initiate",
                java.util.Map.of(
                        "path", request.path(),
                        "filename", request.filename(),
                        "size", request.size()
                ),
                () -> ApiResponse.success(fileService.initiateUpload(
                        currentUser(principal),
                        request
                ))
        );
    }

    @Operation(summary = "完成上传")
    @PostMapping("/upload/complete")
    public ApiResponse<FileMetadataResponse> completeUpload(@AuthenticationPrincipal UserDetails principal,
                                                            @Valid @RequestBody CompleteUploadRequest request) {
        return workspaceRequestProbe.trace(
                "files.upload.complete",
                java.util.Map.of(
                        "path", request.path(),
                        "filename", request.filename(),
                        "size", request.size()
                ),
                () -> ApiResponse.success(fileService.completeUpload(
                        currentUser(principal),
                        request
                ))
        );
    }

    @Operation(summary = "替换文件内容")
    @PatchMapping(value = "/{fileId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileMetadataResponse> updateContent(@AuthenticationPrincipal UserDetails principal,
                                                           @PathVariable Long fileId,
                                                           @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fileService.updateContent(
                currentUser(principal),
                fileId,
                file
        ));
    }

    @Operation(summary = "创建目录")
    @PostMapping("/mkdir")
    public ApiResponse<FileMetadataResponse> mkdir(@AuthenticationPrincipal UserDetails principal,
                                                   @Valid @ModelAttribute MkdirRequest request) {
        return ApiResponse.success(fileService.mkdir(currentUserId(principal), request.path()));
    }

    @Operation(summary = "分页列出文件")
    @GetMapping("/list")
    public ApiResponse<PageResponse<FileMetadataResponse>> list(@AuthenticationPrincipal UserDetails principal,
                                                                @RequestParam(defaultValue = "/") String path,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return workspaceRequestProbe.trace(
                "files.list",
                java.util.Map.of(
                        "path", path,
                        "page", page,
                        "size", size
                ),
                () -> ApiResponse.success(fileService.list(currentUserId(principal), path, page, size))
        );
    }

    @Operation(summary = "最近文件")
    @GetMapping("/recent")
    public ApiResponse<List<FileMetadataResponse>> recent(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.success(fileService.recent(currentUserId(principal)));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/{fileId}/detail")
    public ApiResponse<FileDetailResponse> detail(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable Long fileId) {
        return workspaceRequestProbe.trace(
                "files.detail",
                java.util.Map.of("fileId", fileId),
                () -> {
                    Long userId = currentUserId(principal);
                    FileDetailResponse detail = workspaceRequestProbe.measure(
                            "controller.detail.loadDetail",
                            () -> fileService.detail(userId, fileId)
                    );
                    return ApiResponse.success(detail.withTags(workspaceRequestProbe.measure(
                            "controller.detail.loadTags",
                            () -> workspaceTagService.listFileTags(userId, fileId)
                    )));
                }
        );
    }

    @Operation(summary = "读取压缩包条目")
    @GetMapping("/{fileId}/archive")
    public ApiResponse<WorkspaceArchiveListing> archive(@AuthenticationPrincipal UserDetails principal,
                                                        @PathVariable Long fileId) {
        return workspaceRequestProbe.trace(
                "files.archive.list",
                java.util.Map.of("fileId", fileId),
                () -> ApiResponse.success(fileService.readArchive(
                        currentUserId(principal),
                        fileId
                ))
        );
    }

    @Operation(summary = "下载压缩包内单个条目")
    @GetMapping("/{fileId}/archive/content")
    public ResponseEntity<?> archiveContent(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable Long fileId,
                                            @RequestParam("path") String path) {
        WorkspaceDownloadResult result = workspaceRequestProbe.trace(
                "files.archive.content",
                java.util.Map.of(
                        "fileId", fileId,
                        "path", path
                ),
                () -> workspaceRequestProbe.measure(
                        "controller.archive.content",
                        () -> fileService.downloadArchiveEntry(
                                currentUserId(principal),
                                fileId,
                                path
                        )
                )
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.body());
    }

    @Operation(summary = "列出标签")
    @GetMapping("/tags")
    public ApiResponse<List<WorkspaceTagResponse>> listTags(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.success(workspaceTagService.listTags(
                currentUserId(principal)
        ));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public ApiResponse<WorkspaceTagResponse> createTag(@AuthenticationPrincipal UserDetails principal,
                                                       @Valid @RequestBody CreateWorkspaceTagRequest request) {
        return ApiResponse.success(workspaceTagService.createTag(
                currentUserId(principal),
                request.name(),
                request.color()
        ));
    }

    @Operation(summary = "更新标签")
    @PatchMapping("/tags/{tagId}")
    public ApiResponse<WorkspaceTagResponse> updateTag(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable Long tagId,
                                                       @Valid @RequestBody UpdateWorkspaceTagRequest request) {
        return ApiResponse.success(workspaceTagService.updateTag(
                currentUserId(principal),
                tagId,
                request.name(),
                request.color()
        ));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<List<WorkspaceTagResponse>> deleteTag(@AuthenticationPrincipal UserDetails principal,
                                                             @PathVariable Long tagId) {
        return ApiResponse.success(workspaceTagService.deleteTag(
                currentUserId(principal),
                tagId
        ));
    }

    @Operation(summary = "列出文件标签")
    @GetMapping("/{fileId}/tags")
    public ApiResponse<List<WorkspaceTagResponse>> listFileTags(@AuthenticationPrincipal UserDetails principal,
                                                                @PathVariable Long fileId) {
        return ApiResponse.success(workspaceTagService.listFileTags(
                currentUserId(principal),
                fileId
        ));
    }

    @Operation(summary = "为文件添加标签")
    @PutMapping("/{fileId}/tags/{tagId}")
    public ApiResponse<List<WorkspaceTagResponse>> assignTag(@AuthenticationPrincipal UserDetails principal,
                                                             @PathVariable Long fileId,
                                                             @PathVariable Long tagId) {
        return ApiResponse.success(workspaceTagService.assignTag(
                currentUserId(principal),
                fileId,
                tagId
        ));
    }

    @Operation(summary = "移除文件标签")
    @DeleteMapping("/{fileId}/tags/{tagId}")
    public ApiResponse<List<WorkspaceTagResponse>> removeTag(@AuthenticationPrincipal UserDetails principal,
                                                             @PathVariable Long fileId,
                                                             @PathVariable Long tagId) {
        return ApiResponse.success(workspaceTagService.removeTag(
                currentUserId(principal),
                fileId,
                tagId
        ));
    }

    @Operation(summary = "批量删除文件")
    @PostMapping("/batch/delete")
    public ApiResponse<Void> batchDelete(@AuthenticationPrincipal UserDetails principal,
                                         @Valid @RequestBody BatchFileOperationRequest request) {
        if (request.mode() == null) {
            fileService.batchDelete(
                    currentUserId(principal),
                    request.fileIds()
            );
        } else {
            fileService.batchDelete(
                    currentUserId(principal),
                    request.fileIds(),
                    request.mode()
            );
        }
        return ApiResponse.success();
    }

    @Operation(summary = "收藏文件列表")
    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteFileResponse>> favorites(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.success(fileService.listFavorites(
                currentUserId(principal)
        ));
    }

    @Operation(summary = "收藏文件")
    @PutMapping("/{fileId}/favorite")
    public ApiResponse<FavoriteFileResponse> favorite(@AuthenticationPrincipal UserDetails principal,
                                                      @PathVariable Long fileId) {
        return ApiResponse.success(fileService.setFavorite(
                currentUserId(principal),
                fileId,
                true
        ));
    }

    @Operation(summary = "取消收藏文件")
    @DeleteMapping("/{fileId}/favorite")
    public ApiResponse<FavoriteFileResponse> unfavorite(@AuthenticationPrincipal UserDetails principal,
                                                        @PathVariable Long fileId) {
        return ApiResponse.success(fileService.setFavorite(
                currentUserId(principal),
                fileId,
                false
        ));
    }

    @Operation(summary = "分页列出回收站")
    @GetMapping("/recycle-bin")
    public ApiResponse<PageResponse<RecycleBinItemResponse>> listRecycleBin(@AuthenticationPrincipal UserDetails principal,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(fileService.listRecycleBin(
                currentUserId(principal),
                page,
                size
        ));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> download(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable Long fileId) {
        WorkspaceDownloadResult result = workspaceRequestProbe.trace(
                "files.download",
                java.util.Map.of("fileId", fileId),
                () -> workspaceRequestProbe.measure(
                        "controller.download.execute",
                        () -> fileService.download(
                                currentUserId(principal),
                                fileId
                        )
                )
        );
        if (result.redirect()) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, result.redirectUrl()).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.body());
    }

    @Operation(summary = "通过短时预览令牌获取内嵌预览内容")
    @GetMapping("/viewer/{token}")
    public ResponseEntity<?> viewer(@PathVariable String token) {
        WorkspaceViewerTokenService.ViewerTokenClaims claims = workspaceViewerTokenService.parseViewerToken(token);
        WorkspaceDownloadResult result = workspaceRequestProbe.trace(
                "files.viewer",
                java.util.Map.of("fileId", claims.fileId()),
                () -> workspaceRequestProbe.measure(
                        "controller.viewer.execute",
                        () -> fileService.download(claims.userId(), claims.fileId())
                )
        );
        if (result.redirect()) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, result.redirectUrl()).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.body());
    }

    @Operation(summary = "获取下载链接")
    @GetMapping("/download/{fileId}/url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@AuthenticationPrincipal UserDetails principal,
                                                        @PathVariable Long fileId,
                                                        @RequestParam(name = "viewer", defaultValue = "false") boolean viewer) {
        return workspaceRequestProbe.trace(
                viewer ? "files.viewerUrl" : "files.downloadUrl",
                java.util.Map.of(
                        "fileId", fileId,
                        "viewer", viewer
                ),
                () -> {
                    Long userId = currentUserId(principal);
                    if (viewer) {
                        DownloadUrlResponse response = workspaceRequestProbe.measure(
                                "controller.downloadUrl.viewer",
                                () -> fileService.getViewerSourceUrl(userId, fileId)
                        );
                        if (usesAuthenticatedDownloadEndpoint(response.url(), fileId)) {
                            return ApiResponse.success(new DownloadUrlResponse(
                                    "/api/files/viewer/" + workspaceViewerTokenService.generateViewerToken(userId, fileId)
                            ));
                        }
                        return ApiResponse.success(response);
                    }
                    return ApiResponse.success(workspaceRequestProbe.measure(
                            "controller.downloadUrl.download",
                            () -> fileService.getDownloadUrl(userId, fileId)
                    ));
                }
        );
    }

    @Operation(summary = "重命名文件")
    @PatchMapping("/{fileId}/rename")
    public ApiResponse<FileMetadataResponse> rename(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable Long fileId,
                                                    @Valid @RequestBody RenameFileRequest request) {
        return ApiResponse.success(
                fileService.rename(currentUserId(principal), fileId, request.filename()));
    }

    @Operation(summary = "移动文件")
    @PatchMapping("/{fileId}/move")
    public ApiResponse<WorkspaceMoveResult> move(@AuthenticationPrincipal UserDetails principal,
                                                 @PathVariable Long fileId,
                                                 @Valid @RequestBody MoveFileRequest request) {
        return ApiResponse.success(
                fileService.move(currentUserId(principal), fileId, request.resolvedTargetPath(), request.conflictStrategy()));
    }

    @Operation(summary = "批量移动文件")
    @PostMapping("/batch/move")
    public ApiResponse<WorkspaceMoveResult> batchMove(@AuthenticationPrincipal UserDetails principal,
                                                      @Valid @RequestBody BatchMoveFileRequest request) {
        return ApiResponse.success(
                fileService.batchMove(
                        currentUserId(principal),
                        request.fileIds(),
                        request.targetPath(),
                        request.conflictStrategy()
                ));
    }

    @Operation(summary = "更新文件外观")
    @PatchMapping("/{fileId}/appearance")
    public ApiResponse<FileMetadataResponse> updateAppearance(@AuthenticationPrincipal UserDetails principal,
                                                              @PathVariable Long fileId,
                                                              @RequestBody UpdateWorkspaceAppearanceRequest request) {
        return ApiResponse.success(
                fileService.updateAppearance(
                        currentUserId(principal),
                        fileId,
                        request.customEmoji(),
                        request.folderColor()
                ));
    }

    @Operation(summary = "复制文件")
    @PostMapping("/{fileId}/copy")
    public ApiResponse<FileMetadataResponse> copy(@AuthenticationPrincipal UserDetails principal,
                                                  @PathVariable Long fileId,
                                                  @Valid @RequestBody CopyFileRequest request) {
        return ApiResponse.success(
                fileService.copy(currentUserId(principal), fileId, request.path()));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable Long fileId,
                                    @RequestParam(defaultValue = "RECYCLE") FileDeleteMode mode) {
        fileService.delete(currentUserId(principal), fileId, mode);
        return ApiResponse.success();
    }

    @Operation(summary = "从回收站恢复文件")
    @PostMapping("/recycle-bin/{fileId}/restore")
    public ApiResponse<FileMetadataResponse> restoreRecycleBinItem(@AuthenticationPrincipal UserDetails principal,
                                                                   @PathVariable Long fileId) {
        return ApiResponse.success(fileService.restoreFromRecycleBin(
                currentUserId(principal),
                fileId
        ));
    }

    @Operation(summary = "从回收站永久删除文件")
    @DeleteMapping("/recycle-bin/{fileId}")
    public ApiResponse<Void> permanentlyDeleteRecycleBinItem(@AuthenticationPrincipal UserDetails principal,
                                                             @PathVariable Long fileId) {
        fileService.permanentlyDeleteRecycleBinItem(currentUserId(principal), fileId);
        return ApiResponse.success();
    }

    private WorkspaceUserContext currentUser(UserDetails principal) {
        return workspaceRequestProbe.measure("controller.resolveUser", () -> {
            if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
                return new WorkspaceUserContext(
                        authenticatedUserPrincipal.getUserId(),
                        authenticatedUserPrincipal.getStorageQuotaBytes(),
                        authenticatedUserPrincipal.getMaxUploadSizeBytes()
                );
            }
            AuthenticatedUserPrincipal authenticatedUserPrincipal = (AuthenticatedUserPrincipal) userDetailsService
                    .loadUserByUsername(principal.getUsername());
            return new WorkspaceUserContext(
                    authenticatedUserPrincipal.getUserId(),
                    authenticatedUserPrincipal.getStorageQuotaBytes(),
                    authenticatedUserPrincipal.getMaxUploadSizeBytes()
            );
        });
    }

    private Long currentUserId(UserDetails principal) {
        return workspaceRequestProbe.measure("controller.resolveUserId", () -> {
            if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
                return authenticatedUserPrincipal.getUserId();
            }
            return userDetailsService.loadUserId(principal.getUsername());
        });
    }

    private boolean usesAuthenticatedDownloadEndpoint(String url, Long fileId) {
        return ("/api/files/download/" + fileId).equals(url);
    }
}
