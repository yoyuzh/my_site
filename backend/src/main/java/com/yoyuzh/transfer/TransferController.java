package com.yoyuzh.transfer;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final CustomUserDetailsService userDetailsService;

    @Operation(summary = "创建快传会话")
    @PostMapping("/sessions")
    public ApiResponse<TransferSessionResponse> createSession(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody CreateTransferSessionRequest request) {
        requireAuthenticatedUser(userDetails);
        userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiResponse.success(transferService.createSession(request));
    }

    @Operation(summary = "通过取件码查找快传会话")
    @GetMapping("/sessions/lookup")
    public ApiResponse<LookupTransferSessionResponse> lookupSession(@RequestParam String pickupCode) {
        return ApiResponse.success(transferService.lookupSession(pickupCode));
    }

    @Operation(summary = "加入快传会话")
    @PostMapping("/sessions/{sessionId}/join")
    public ApiResponse<TransferSessionResponse> joinSession(@PathVariable String sessionId) {
        return ApiResponse.success(transferService.joinSession(sessionId));
    }

    @Operation(summary = "提交快传信令")
    @PostMapping("/sessions/{sessionId}/signals")
    public ApiResponse<Void> postSignal(@PathVariable String sessionId,
                                        @RequestParam String role,
                                        @Valid @RequestBody TransferSignalRequest request) {
        transferService.postSignal(sessionId, role, request);
        return ApiResponse.success();
    }

    @Operation(summary = "轮询快传信令")
    @GetMapping("/sessions/{sessionId}/signals")
    public ApiResponse<PollTransferSignalsResponse> pollSignals(@PathVariable String sessionId,
                                                                @RequestParam String role,
                                                                @RequestParam(defaultValue = "0") long after) {
        return ApiResponse.success(transferService.pollSignals(sessionId, role, after));
    }

    private void requireAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户未登录");
        }
    }
}
