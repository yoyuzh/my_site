package com.yoyuzh.transfer.internal.web;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.RemoteDownloadApi;
import com.yoyuzh.transfer.api.RemoteDownloadDetailResponse;
import com.yoyuzh.transfer.api.RemoteDownloadListItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transfer/remote-downloads")
@RequiredArgsConstructor
public class RemoteDownloadController {

    private final RemoteDownloadApi remoteDownloadApi;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;

    @PostMapping
    public ApiResponse<RemoteDownloadDetailResponse> create(@AuthenticationPrincipal UserDetails userDetails,
                                                            @Valid @ModelAttribute CreateRemoteDownloadRequest request) {
        return ApiResponse.success(remoteDownloadApi.create(currentUserId(userDetails), request.toCommand()));
    }

    @GetMapping
    public ApiResponse<List<RemoteDownloadListItemResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(remoteDownloadApi.listOwned(currentUserId(userDetails)));
    }

    @GetMapping("/{id}")
    public ApiResponse<RemoteDownloadDetailResponse> get(@AuthenticationPrincipal UserDetails userDetails,
                                                         @PathVariable Long id) {
        return ApiResponse.success(remoteDownloadApi.getOwned(currentUserId(userDetails), id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<RemoteDownloadDetailResponse> retry(@AuthenticationPrincipal UserDetails userDetails,
                                                           @PathVariable Long id) {
        return ApiResponse.success(remoteDownloadApi.retry(currentUserId(userDetails), id));
    }

    @PostMapping("/{id}/selection")
    public ApiResponse<RemoteDownloadDetailResponse> selectFiles(@AuthenticationPrincipal UserDetails userDetails,
                                                                 @PathVariable Long id,
                                                                 @Valid @RequestBody SelectRemoteDownloadFilesRequest request) {
        return ApiResponse.success(remoteDownloadApi.selectFiles(currentUserId(userDetails), id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<RemoteDownloadDetailResponse> cancel(@AuthenticationPrincipal UserDetails userDetails,
                                                            @PathVariable Long id) {
        return ApiResponse.success(remoteDownloadApi.cancel(currentUserId(userDetails), id));
    }

    private Long currentUserId(UserDetails userDetails) {
        return identityUserDirectoryApi.findProfileByUsername(userDetails.getUsername())
                .map(IdentityUserProfileSummary::id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
    }
}
