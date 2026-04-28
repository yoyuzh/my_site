package com.yoyuzh.files.workspace.internal.web;

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
import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
import com.yoyuzh.files.workspace.api.WorkspaceMoveResult;
import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.application.FileViewerConfigService;
import com.yoyuzh.files.workspace.internal.application.WorkspaceTagService;
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

    @Operation(summary = "获取文件打开方式配置")
    @GetMapping("/viewers/config")
    public ApiResponse<FileViewerConfigResponse> viewerConfig() {
        return ApiResponse.success(fileViewerConfigService.defaultConfig());
    }

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<FileMetadataResponse> upload(@AuthenticationPrincipal UserDetails userDetails,
                                                    @RequestParam(defaultValue = "/") String path,
                                                    @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fileService.upload(userDetailsService.loadAuthenticatedUser(userDetails.getUsername()), path, file));
    }

    @Operation(summary = "初始化上传")
    @PostMapping("/upload/initiate")
    public ApiResponse<InitiateUploadResponse> initiateUpload(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody InitiateUploadRequest request) {
        return ApiResponse.success(fileService.initiateUpload(
                userDetailsService.loadAuthenticatedUser(userDetails.getUsername()),
                request
        ));
    }

    @Operation(summary = "完成上传")
    @PostMapping("/upload/complete")
    public ApiResponse<FileMetadataResponse> completeUpload(@AuthenticationPrincipal UserDetails userDetails,
                                                            @Valid @RequestBody CompleteUploadRequest request) {
        return ApiResponse.success(fileService.completeUpload(
                userDetailsService.loadAuthenticatedUser(userDetails.getUsername()),
                request
        ));
    }

    @Operation(summary = "替换文件内容")
    @PatchMapping(value = "/{fileId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileMetadataResponse> updateContent(@AuthenticationPrincipal UserDetails userDetails,
                                                           @PathVariable Long fileId,
                                                           @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fileService.updateContent(
                userDetailsService.loadAuthenticatedUser(userDetails.getUsername()),
                fileId,
                file
        ));
    }

    @Operation(summary = "创建目录")
    @PostMapping("/mkdir")
    public ApiResponse<FileMetadataResponse> mkdir(@AuthenticationPrincipal UserDetails userDetails,
                                                   @Valid @ModelAttribute MkdirRequest request) {
        return ApiResponse.success(fileService.mkdir(currentUserId(userDetails), request.path()));
    }

    @Operation(summary = "分页列出文件")
    @GetMapping("/list")
    public ApiResponse<PageResponse<FileMetadataResponse>> list(@AuthenticationPrincipal UserDetails userDetails,
                                                                @RequestParam(defaultValue = "/") String path,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(fileService.list(currentUserId(userDetails), path, page, size));
    }

    @Operation(summary = "最近文件")
    @GetMapping("/recent")
    public ApiResponse<List<FileMetadataResponse>> recent(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(fileService.recent(currentUserId(userDetails)));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/{fileId}/detail")
    public ApiResponse<FileDetailResponse> detail(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long fileId) {
        Long userId = currentUserId(userDetails);
        FileDetailResponse detail = fileService.detail(
                userId,
                fileId
        );
        return ApiResponse.success(detail.withTags(workspaceTagService.listFileTags(
                userId,
                fileId
        )));
    }

    @Operation(summary = "列出标签")
    @GetMapping("/tags")
    public ApiResponse<List<WorkspaceTagResponse>> listTags(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(workspaceTagService.listTags(
                currentUserId(userDetails)
        ));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public ApiResponse<WorkspaceTagResponse> createTag(@AuthenticationPrincipal UserDetails userDetails,
                                                       @Valid @RequestBody CreateWorkspaceTagRequest request) {
        return ApiResponse.success(workspaceTagService.createTag(
                currentUserId(userDetails),
                request.name(),
                request.color()
        ));
    }

    @Operation(summary = "更新标签")
    @PatchMapping("/tags/{tagId}")
    public ApiResponse<WorkspaceTagResponse> updateTag(@AuthenticationPrincipal UserDetails userDetails,
                                                       @PathVariable Long tagId,
                                                       @Valid @RequestBody UpdateWorkspaceTagRequest request) {
        return ApiResponse.success(workspaceTagService.updateTag(
                currentUserId(userDetails),
                tagId,
                request.name(),
                request.color()
        ));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<List<WorkspaceTagResponse>> deleteTag(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable Long tagId) {
        return ApiResponse.success(workspaceTagService.deleteTag(
                currentUserId(userDetails),
                tagId
        ));
    }

    @Operation(summary = "列出文件标签")
    @GetMapping("/{fileId}/tags")
    public ApiResponse<List<WorkspaceTagResponse>> listFileTags(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable Long fileId) {
        return ApiResponse.success(workspaceTagService.listFileTags(
                currentUserId(userDetails),
                fileId
        ));
    }

    @Operation(summary = "为文件添加标签")
    @PutMapping("/{fileId}/tags/{tagId}")
    public ApiResponse<List<WorkspaceTagResponse>> assignTag(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable Long fileId,
                                                             @PathVariable Long tagId) {
        return ApiResponse.success(workspaceTagService.assignTag(
                currentUserId(userDetails),
                fileId,
                tagId
        ));
    }

    @Operation(summary = "移除文件标签")
    @DeleteMapping("/{fileId}/tags/{tagId}")
    public ApiResponse<List<WorkspaceTagResponse>> removeTag(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable Long fileId,
                                                             @PathVariable Long tagId) {
        return ApiResponse.success(workspaceTagService.removeTag(
                currentUserId(userDetails),
                fileId,
                tagId
        ));
    }

    @Operation(summary = "批量删除文件")
    @PostMapping("/batch/delete")
    public ApiResponse<Void> batchDelete(@AuthenticationPrincipal UserDetails userDetails,
                                         @Valid @RequestBody BatchFileOperationRequest request) {
        if (request.mode() == null) {
            fileService.batchDelete(
                    currentUserId(userDetails),
                    request.fileIds()
            );
        } else {
            fileService.batchDelete(
                    currentUserId(userDetails),
                    request.fileIds(),
                    request.mode()
            );
        }
        return ApiResponse.success();
    }

    @Operation(summary = "收藏文件列表")
    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteFileResponse>> favorites(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(fileService.listFavorites(
                currentUserId(userDetails)
        ));
    }

    @Operation(summary = "收藏文件")
    @PutMapping("/{fileId}/favorite")
    public ApiResponse<FavoriteFileResponse> favorite(@AuthenticationPrincipal UserDetails userDetails,
                                                      @PathVariable Long fileId) {
        return ApiResponse.success(fileService.setFavorite(
                currentUserId(userDetails),
                fileId,
                true
        ));
    }

    @Operation(summary = "取消收藏文件")
    @DeleteMapping("/{fileId}/favorite")
    public ApiResponse<FavoriteFileResponse> unfavorite(@AuthenticationPrincipal UserDetails userDetails,
                                                        @PathVariable Long fileId) {
        return ApiResponse.success(fileService.setFavorite(
                currentUserId(userDetails),
                fileId,
                false
        ));
    }

    @Operation(summary = "分页列出回收站")
    @GetMapping("/recycle-bin")
    public ApiResponse<PageResponse<RecycleBinItemResponse>> listRecycleBin(@AuthenticationPrincipal UserDetails userDetails,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(fileService.listRecycleBin(
                currentUserId(userDetails),
                page,
                size
        ));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> download(@AuthenticationPrincipal UserDetails userDetails,
                                      @PathVariable Long fileId) {
        WorkspaceDownloadResult result = fileService.download(
                currentUserId(userDetails),
                fileId
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

    @Operation(summary = "获取下载链接")
    @GetMapping("/download/{fileId}/url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@AuthenticationPrincipal UserDetails userDetails,
                                                        @PathVariable Long fileId,
                                                        @RequestParam(name = "viewer", defaultValue = "false") boolean viewer) {
        if (viewer) {
            return ApiResponse.success(fileService.getViewerSourceUrl(
                    currentUserId(userDetails),
                    fileId
            ));
        }
        return ApiResponse.success(fileService.getDownloadUrl(
                currentUserId(userDetails),
                fileId
        ));
    }

    @Operation(summary = "重命名文件")
    @PatchMapping("/{fileId}/rename")
    public ApiResponse<FileMetadataResponse> rename(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable Long fileId,
                                                    @Valid @RequestBody RenameFileRequest request) {
        return ApiResponse.success(
                fileService.rename(currentUserId(userDetails), fileId, request.filename()));
    }

    @Operation(summary = "移动文件")
    @PatchMapping("/{fileId}/move")
    public ApiResponse<WorkspaceMoveResult> move(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PathVariable Long fileId,
                                                 @Valid @RequestBody MoveFileRequest request) {
        return ApiResponse.success(
                fileService.move(currentUserId(userDetails), fileId, request.resolvedTargetPath(), request.conflictStrategy()));
    }

    @Operation(summary = "批量移动文件")
    @PostMapping("/batch/move")
    public ApiResponse<WorkspaceMoveResult> batchMove(@AuthenticationPrincipal UserDetails userDetails,
                                                      @Valid @RequestBody BatchMoveFileRequest request) {
        return ApiResponse.success(
                fileService.batchMove(
                        currentUserId(userDetails),
                        request.fileIds(),
                        request.targetPath(),
                        request.conflictStrategy()
                ));
    }

    @Operation(summary = "更新文件外观")
    @PatchMapping("/{fileId}/appearance")
    public ApiResponse<FileMetadataResponse> updateAppearance(@AuthenticationPrincipal UserDetails userDetails,
                                                              @PathVariable Long fileId,
                                                              @RequestBody UpdateWorkspaceAppearanceRequest request) {
        return ApiResponse.success(
                fileService.updateAppearance(
                        currentUserId(userDetails),
                        fileId,
                        request.customEmoji(),
                        request.folderColor()
                ));
    }

    @Operation(summary = "复制文件")
    @PostMapping("/{fileId}/copy")
    public ApiResponse<FileMetadataResponse> copy(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long fileId,
                                                  @Valid @RequestBody CopyFileRequest request) {
        return ApiResponse.success(
                fileService.copy(currentUserId(userDetails), fileId, request.path()));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long fileId,
                                    @RequestParam(defaultValue = "RECYCLE") FileDeleteMode mode) {
        fileService.delete(currentUserId(userDetails), fileId, mode);
        return ApiResponse.success();
    }

    @Operation(summary = "从回收站恢复文件")
    @PostMapping("/recycle-bin/{fileId}/restore")
    public ApiResponse<FileMetadataResponse> restoreRecycleBinItem(@AuthenticationPrincipal UserDetails userDetails,
                                                                   @PathVariable Long fileId) {
        return ApiResponse.success(fileService.restoreFromRecycleBin(
                currentUserId(userDetails),
                fileId
        ));
    }

    @Operation(summary = "从回收站永久删除文件")
    @DeleteMapping("/recycle-bin/{fileId}")
    public ApiResponse<Void> permanentlyDeleteRecycleBinItem(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable Long fileId) {
        fileService.permanentlyDeleteRecycleBinItem(currentUserId(userDetails), fileId);
        return ApiResponse.success();
    }

    private Long currentUserId(UserDetails userDetails) {
        return userDetailsService.loadUserId(userDetails.getUsername());
    }
}
