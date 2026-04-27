package com.yoyuzh.transfer.internal.web;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.sharing.api.ImportSharedFileRequest;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.transfer.api.LookupTransferSessionResponse;
import com.yoyuzh.transfer.api.OfflineDownloadResult;
import com.yoyuzh.transfer.api.PollTransferSignalsResponse;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferImportCommand;
import com.yoyuzh.transfer.api.TransferSessionApi;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import com.yoyuzh.transfer.api.TransferSignalRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferSessionApi transferSessionApi;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;

    @Operation(summary = "创建快传会话")
    @PostMapping("/sessions")
    public ApiResponse<TransferSessionResponse> createSession(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody CreateTransferSessionRequest request) {
        Long senderUserId = loadAuthenticatedUserId(userDetails);
        return ApiResponse.success(transferSessionApi.createSession(senderUserId, new CreateTransferSessionCommand(
                request.mode(),
                request.files()
        )));
    }

    @Operation(summary = "通过取件码查找快传会话")
    @GetMapping("/sessions/lookup")
    public ApiResponse<LookupTransferSessionResponse> lookupSession(@RequestParam String pickupCode) {
        return ApiResponse.success(transferSessionApi.lookupSession(pickupCode));
    }

    @Operation(summary = "加入快传会话")
    @PostMapping("/sessions/{sessionId}/join")
    public ApiResponse<TransferSessionResponse> joinSession(@PathVariable String sessionId) {
        return ApiResponse.success(transferSessionApi.joinSession(sessionId));
    }

    @Operation(summary = "查看当前用户的离线快传列表")
    @GetMapping("/sessions/offline/mine")
    public ApiResponse<java.util.List<TransferSessionResponse>> listOfflineSessions(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(transferSessionApi.listOfflineSessions(currentUserId(userDetails)));
    }

    @Operation(summary = "上传离线快传文件")
    @PostMapping("/sessions/{sessionId}/files/{fileId}/content")
    public ApiResponse<Void> uploadOfflineFile(@AuthenticationPrincipal UserDetails userDetails,
                                               @PathVariable String sessionId,
                                               @PathVariable String fileId,
                                               @RequestPart("file") MultipartFile file) {
        transferSessionApi.uploadOfflineFile(
                currentUserId(userDetails),
                sessionId,
                fileId,
                file
        );
        return ApiResponse.success();
    }

    @Operation(summary = "下载离线快传文件")
    @GetMapping("/sessions/{sessionId}/files/{fileId}/download")
    public ResponseEntity<?> downloadOfflineFile(@PathVariable String sessionId,
                                                 @PathVariable String fileId) {
        return toResponseEntity(transferSessionApi.downloadOfflineFile(sessionId, fileId));
    }

    @Operation(summary = "把离线快传文件存入网盘")
    @PostMapping("/sessions/{sessionId}/files/{fileId}/import")
    public ApiResponse<FileMetadataResponse> importOfflineFile(@AuthenticationPrincipal UserDetails userDetails,
                                                               @PathVariable String sessionId,
                                                               @PathVariable String fileId,
                                                               @Valid @RequestBody ImportSharedFileRequest request) {
        return ApiResponse.success(transferSessionApi.importOfflineFile(
                currentUserId(userDetails),
                sessionId,
                fileId,
                new TransferImportCommand(request.path())
        ));
    }

    @Operation(summary = "提交快传信令")
    @PostMapping("/sessions/{sessionId}/signals")
    public ApiResponse<Void> postSignal(@PathVariable String sessionId,
                                        @RequestParam String role,
                                        @Valid @RequestBody TransferSignalRequest request) {
        transferSessionApi.postSignal(sessionId, role, request);
        return ApiResponse.success();
    }

    @Operation(summary = "轮询快传信令")
    @GetMapping("/sessions/{sessionId}/signals")
    public ApiResponse<PollTransferSignalsResponse> pollSignals(@PathVariable String sessionId,
                                                                @RequestParam String role,
                                                                @RequestParam(defaultValue = "0") long after) {
        return ApiResponse.success(transferSessionApi.pollSignals(sessionId, role, after));
    }

    private void requireAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户未登录");
        }
    }

    private Long loadAuthenticatedUserId(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return resolveUserId(userDetails.getUsername());
    }

    private Long currentUserId(UserDetails userDetails) {
        requireAuthenticatedUser(userDetails);
        return resolveUserId(userDetails.getUsername());
    }

    private Long resolveUserId(String username) {
        return identityUserDirectoryApi.findProfileByUsername(username)
                .map(IdentityUserProfileSummary::id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
    }

    private ResponseEntity<?> toResponseEntity(OfflineDownloadResult result) {
        if (result.redirect()) {
            return ResponseEntity.status(302).location(URI.create(result.redirectUrl())).build();
        }
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(result.filename(), StandardCharsets.UTF_8)
                )
                .contentType(MediaType.parseMediaType(result.contentType()));
        if (result.contentLength() != null) {
            responseBuilder.contentLength(result.contentLength());
        }
        try {
            return responseBuilder.body(new InputStreamResource(result.body().getInputStream()));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "offline transfer download stream open failed");
        }
    }
}
