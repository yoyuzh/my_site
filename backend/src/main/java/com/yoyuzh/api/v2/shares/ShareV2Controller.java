package com.yoyuzh.api.v2.shares;

import com.yoyuzh.api.v2.ApiV2Response;
import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/shares")
@RequiredArgsConstructor
public class ShareV2Controller {

    private final SharingApi sharingApi;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping
    public ApiV2Response<ShareV2Response> createShare(@AuthenticationPrincipal UserDetails userDetails,
                                                      @Valid @RequestBody CreateShareV2Request request) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(sharingApi.createShare(user, new CreateShareCommand(
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

    @GetMapping(value = "/{token}", params = "download")
    public ResponseEntity<?> downloadShare(@PathVariable String token,
                                           @RequestParam(required = false) String password) {
        return sharingApi.downloadSharedFile(token, password);
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
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(sharingApi.importSharedFile(user, token, new ImportShareCommand(request.path(), request.password())));
    }

    @GetMapping("/mine")
    public ApiV2Response<PageResponse<ShareV2Response>> mine(@AuthenticationPrincipal UserDetails userDetails,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        var result = sharingApi.listOwnedShares(user, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiV2Response.success(new PageResponse<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @DeleteMapping("/{id}")
    public ApiV2Response<Void> deleteShare(@AuthenticationPrincipal UserDetails userDetails,
                                           @PathVariable Long id) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        sharingApi.deleteOwnedShare(user, id);
        return ApiV2Response.success(null);
    }
}
