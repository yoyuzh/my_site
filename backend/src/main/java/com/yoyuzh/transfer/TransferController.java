package com.yoyuzh.transfer;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.share.ImportSharedFileRequest;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.TransferImportCommand;
import com.yoyuzh.transfer.api.TransferSessionApi;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final CustomUserDetailsService userDetailsService;

    @Operation(summary = "创建快传会话")
    @PostMapping("/sessions")
    public ApiResponse<TransferSessionResponse> createSession(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody CreateTransferSessionRequest request) {
        User sender = loadAuthenticatedUser(userDetails);
        return ApiResponse.success(transferSessionApi.createSession(sender, new CreateTransferSessionCommand(
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
        requireAuthenticatedUser(userDetails);
        return ApiResponse.success(transferSessionApi.listOfflineSessions(
                userDetailsService.loadDomainUser(userDetails.getUsername())
        ));
    }

    @Operation(summary = "上传离线快传文件")
    @PostMapping("/sessions/{sessionId}/files/{fileId}/content")
    public ApiResponse<Void> uploadOfflineFile(@AuthenticationPrincipal UserDetails userDetails,
                                               @PathVariable String sessionId,
                                               @PathVariable String fileId,
                                               @RequestPart("file") MultipartFile file) {
        requireAuthenticatedUser(userDetails);
        transferSessionApi.uploadOfflineFile(
                userDetailsService.loadDomainUser(userDetails.getUsername()),
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
        return transferSessionApi.downloadOfflineFile(sessionId, fileId);
    }

    @Operation(summary = "把离线快传文件存入网盘")
    @PostMapping("/sessions/{sessionId}/files/{fileId}/import")
    public ApiResponse<FileMetadataResponse> importOfflineFile(@AuthenticationPrincipal UserDetails userDetails,
                                                               @PathVariable String sessionId,
                                                               @PathVariable String fileId,
                                                               @Valid @RequestBody ImportSharedFileRequest request) {
        requireAuthenticatedUser(userDetails);
        return ApiResponse.success(transferSessionApi.importOfflineFile(
                userDetailsService.loadDomainUser(userDetails.getUsername()),
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

    private User loadAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userDetailsService.loadDomainUser(userDetails.getUsername());
    }
}
