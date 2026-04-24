package com.yoyuzh.files.sharing.internal.web;

import com.yoyuzh.boot.web.v2.ApiV2Response;
import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.ShareDownloadResult;
import com.yoyuzh.files.sharing.api.ShareStatsResponse;
import com.yoyuzh.files.sharing.api.ShareV2Response;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v2/shares")
@RequiredArgsConstructor
public class ShareV2Controller {

    private final SharingApi sharingApi;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping
    public ApiV2Response<ShareV2Response> createShare(@AuthenticationPrincipal UserDetails userDetails,
                                                      @Valid @RequestBody CreateShareV2Request request) {
        return ApiV2Response.success(sharingApi.createShare(currentUserId(userDetails), new CreateShareCommand(
                request.fileId(),
                request.password(),
                request.shareName(),
                request.allowImport(),
                request.allowDownload(),
                request.expiresAt(),
                request.maxDownloads()
        )));
    }

    @GetMapping("/{token}")
    public ApiV2Response<ShareV2Response> getShare(@PathVariable String token) {
        return ApiV2Response.success(sharingApi.getShare(token));
    }

    @GetMapping("/{token}/stats")
    public ApiV2Response<ShareStatsResponse> stats(@AuthenticationPrincipal UserDetails userDetails,
                                                   @PathVariable String token) {
        return ApiV2Response.success(sharingApi.getStats(currentUserId(userDetails), token));
    }

    @GetMapping(value = "/{token}", params = "download")
    public ResponseEntity<?> downloadShare(@PathVariable String token,
                                           @RequestParam(required = false) String password) {
        return toResponseEntity(sharingApi.downloadSharedFile(token, password));
    }

    @PostMapping("/{token}/verify-password")
    public ApiV2Response<ShareV2Response> verifyPassword(@PathVariable String token,
                                                          @Valid @RequestBody VerifySharePasswordV2Request request) {
        return ApiV2Response.success(sharingApi.verifyPassword(token, request.password()));
    }

    @PostMapping("/{token}/import")
    public ApiV2Response<FileMetadataResponse> importSharedFile(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String token,
                                                                @Valid @RequestBody ImportShareV2Request request) {
        return ApiV2Response.success(sharingApi.importSharedFile(currentUserId(userDetails), token, new ImportShareCommand(request.path(), request.password())));
    }

    @GetMapping("/mine")
    public ApiV2Response<PageResponse<ShareV2Response>> mine(@AuthenticationPrincipal UserDetails userDetails,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = sharingApi.listOwnedShares(currentUserId(userDetails), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiV2Response.success(new PageResponse<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @PatchMapping("/{id}/policy")
    public ApiV2Response<ShareV2Response> updatePolicy(@AuthenticationPrincipal UserDetails userDetails,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody UpdateSharePolicyV2Request request) {
        return ApiV2Response.success(sharingApi.updatePolicy(currentUserId(userDetails), id, request.maxDownloads()));
    }

    @DeleteMapping("/{id}")
    public ApiV2Response<Void> deleteShare(@AuthenticationPrincipal UserDetails userDetails,
                                           @PathVariable Long id) {
        sharingApi.deleteOwnedShare(currentUserId(userDetails), id);
        return ApiV2Response.success(null);
    }

    private Long currentUserId(UserDetails userDetails) {
        return userDetailsService.loadUserId(userDetails.getUsername());
    }

    private ResponseEntity<?> toResponseEntity(ShareDownloadResult result) {
        if (result.redirect()) {
            return ResponseEntity.status(302).location(URI.create(result.redirectUrl())).build();
        }
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(result.filename(), StandardCharsets.UTF_8)
                )
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.body());
    }
}
