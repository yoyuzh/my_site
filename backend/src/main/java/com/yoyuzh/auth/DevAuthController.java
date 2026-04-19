package com.yoyuzh.auth;

import com.yoyuzh.auth.dto.AuthResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final AuthService authService;

    @Operation(summary = "开发环境免密登录")
    @PostMapping("/dev-login")
    public ApiResponse<AuthResponse> devLogin(@RequestParam(required = false) String username,
                                              @RequestHeader(name = AuthClientType.HEADER_NAME, required = false) String clientTypeHeader) {
        return ApiResponse.success(authService.devLogin(username, AuthClientType.fromHeader(clientTypeHeader)));
    }
}
