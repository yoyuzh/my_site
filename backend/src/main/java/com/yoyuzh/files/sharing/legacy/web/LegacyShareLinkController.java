package com.yoyuzh.files.sharing.legacy.web;

import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.files.sharing.api.CreateFileShareLinkResponse;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.FileShareDetailsResponse;
import com.yoyuzh.files.sharing.api.ImportSharedFileRequest;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.ShareV2Response;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class LegacyShareLinkController {

    private final SharingApi sharingApi;
    private final CustomUserDetailsService userDetailsService;

    @Operation(summary = "创建分享链接")
    @PostMapping("/{fileId}/share-links")
    public ApiResponse<CreateFileShareLinkResponse> createShareLink(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @PathVariable Long fileId) {
        ShareV2Response response = sharingApi.createShare(currentUserId(userDetails), new CreateShareCommand(
                fileId,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        if (response.file() == null) {
            throw new BusinessException(ErrorCode.UNKNOWN, "share file metadata missing");
        }
        return ApiResponse.success(new CreateFileShareLinkResponse(
                response.token(),
                response.file().filename(),
                response.file().size(),
                response.file().contentType(),
                response.createdAt()
        ));
    }

    @Operation(summary = "查看分享详情")
    @GetMapping("/share-links/{token}")
    public ApiResponse<FileShareDetailsResponse> getShareDetails(@PathVariable String token) {
        ShareV2Response response = sharingApi.getShare(token);
        if (response.file() == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "该分享链接需要先验证提取码");
        }
        return ApiResponse.success(new FileShareDetailsResponse(
                response.token(),
                response.ownerUsername(),
                response.file().filename(),
                response.file().size(),
                response.file().contentType(),
                response.file().directory(),
                response.createdAt()
        ));
    }

    @Operation(summary = "导入共享文件")
    @PostMapping("/share-links/{token}/import")
    public ApiResponse<FileMetadataResponse> importSharedFile(@AuthenticationPrincipal UserDetails userDetails,
                                                              @PathVariable String token,
                                                              @Valid @RequestBody ImportSharedFileRequest request) {
        return ApiResponse.success(sharingApi.importSharedFile(
                currentUserId(userDetails),
                token,
                new ImportShareCommand(request.path(), null)
        ));
    }

    private Long currentUserId(UserDetails userDetails) {
        return userDetailsService.loadUserId(userDetails.getUsername());
    }
}
