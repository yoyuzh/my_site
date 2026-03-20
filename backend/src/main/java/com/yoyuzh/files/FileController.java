package com.yoyuzh.files;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final CustomUserDetailsService userDetailsService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ApiResponse<FileMetadataResponse> upload(@AuthenticationPrincipal UserDetails userDetails,
                                                    @RequestParam(defaultValue = "/") String path,
                                                    @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fileService.upload(userDetailsService.loadDomainUser(userDetails.getUsername()), path, file));
    }

    @Operation(summary = "初始化上传")
    @PostMapping("/upload/initiate")
    public ApiResponse<InitiateUploadResponse> initiateUpload(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody InitiateUploadRequest request) {
        return ApiResponse.success(fileService.initiateUpload(
                userDetailsService.loadDomainUser(userDetails.getUsername()),
                request
        ));
    }

    @Operation(summary = "完成上传")
    @PostMapping("/upload/complete")
    public ApiResponse<FileMetadataResponse> completeUpload(@AuthenticationPrincipal UserDetails userDetails,
                                                            @Valid @RequestBody CompleteUploadRequest request) {
        return ApiResponse.success(fileService.completeUpload(
                userDetailsService.loadDomainUser(userDetails.getUsername()),
                request
        ));
    }

    @Operation(summary = "创建目录")
    @PostMapping("/mkdir")
    public ApiResponse<FileMetadataResponse> mkdir(@AuthenticationPrincipal UserDetails userDetails,
                                                   @Valid @ModelAttribute MkdirRequest request) {
        return ApiResponse.success(fileService.mkdir(userDetailsService.loadDomainUser(userDetails.getUsername()), request.path()));
    }

    @Operation(summary = "分页列出文件")
    @GetMapping("/list")
    public ApiResponse<PageResponse<FileMetadataResponse>> list(@AuthenticationPrincipal UserDetails userDetails,
                                                                @RequestParam(defaultValue = "/") String path,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(fileService.list(userDetailsService.loadDomainUser(userDetails.getUsername()), path, page, size));
    }

    @Operation(summary = "最近文件")
    @GetMapping("/recent")
    public ApiResponse<List<FileMetadataResponse>> recent(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(fileService.recent(userDetailsService.loadDomainUser(userDetails.getUsername())));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> download(@AuthenticationPrincipal UserDetails userDetails,
                                      @PathVariable Long fileId) {
        return fileService.download(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId);
    }

    @Operation(summary = "获取下载链接")
    @GetMapping("/download/{fileId}/url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@AuthenticationPrincipal UserDetails userDetails,
                                                        @PathVariable Long fileId) {
        return ApiResponse.success(fileService.getDownloadUrl(
                userDetailsService.loadDomainUser(userDetails.getUsername()),
                fileId
        ));
    }

    @Operation(summary = "重命名文件")
    @PatchMapping("/{fileId}/rename")
    public ApiResponse<FileMetadataResponse> rename(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable Long fileId,
                                                    @Valid @RequestBody RenameFileRequest request) {
        return ApiResponse.success(
                fileService.rename(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId, request.filename()));
    }

    @Operation(summary = "移动文件")
    @PatchMapping("/{fileId}/move")
    public ApiResponse<FileMetadataResponse> move(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long fileId,
                                                  @Valid @RequestBody MoveFileRequest request) {
        return ApiResponse.success(
                fileService.move(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId, request.path()));
    }

    @Operation(summary = "复制文件")
    @PostMapping("/{fileId}/copy")
    public ApiResponse<FileMetadataResponse> copy(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable Long fileId,
                                                  @Valid @RequestBody CopyFileRequest request) {
        return ApiResponse.success(
                fileService.copy(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId, request.path()));
    }

    @Operation(summary = "创建分享链接")
    @PostMapping("/{fileId}/share-links")
    public ApiResponse<CreateFileShareLinkResponse> createShareLink(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @PathVariable Long fileId) {
        return ApiResponse.success(
                fileService.createShareLink(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId)
        );
    }

    @Operation(summary = "查看分享详情")
    @GetMapping("/share-links/{token}")
    public ApiResponse<FileShareDetailsResponse> getShareDetails(@PathVariable String token) {
        return ApiResponse.success(fileService.getShareDetails(token));
    }

    @Operation(summary = "导入共享文件")
    @PostMapping("/share-links/{token}/import")
    public ApiResponse<FileMetadataResponse> importSharedFile(@AuthenticationPrincipal UserDetails userDetails,
                                                              @PathVariable String token,
                                                              @Valid @RequestBody ImportSharedFileRequest request) {
        return ApiResponse.success(
                fileService.importSharedFile(
                        userDetailsService.loadDomainUser(userDetails.getUsername()),
                        token,
                        request.path()
                )
        );
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long fileId) {
        fileService.delete(userDetailsService.loadDomainUser(userDetails.getUsername()), fileId);
        return ApiResponse.success();
    }
}
