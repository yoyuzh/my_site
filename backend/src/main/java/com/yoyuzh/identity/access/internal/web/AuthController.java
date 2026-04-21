package com.yoyuzh.identity.access.internal.web;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.internal.application.AuthService;
import com.yoyuzh.identity.access.api.AuthResponse;
import com.yoyuzh.identity.access.api.LoginRequest;
import com.yoyuzh.identity.access.api.RefreshTokenRequest;
import com.yoyuzh.identity.access.api.RegisterRequest;
import com.yoyuzh.shared.kernel.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                              @RequestHeader(name = IdentityClientType.HEADER_NAME, required = false) String clientTypeHeader) {
        return ApiResponse.success(authService.register(request, IdentityClientType.fromHeader(clientTypeHeader)));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           @RequestHeader(name = IdentityClientType.HEADER_NAME, required = false) String clientTypeHeader) {
        return ApiResponse.success(authService.login(request, IdentityClientType.fromHeader(clientTypeHeader)));
    }

    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                             @RequestHeader(name = IdentityClientType.HEADER_NAME, required = false) String clientTypeHeader) {
        return ApiResponse.success(authService.refresh(request.refreshToken(), IdentityClientType.fromHeader(clientTypeHeader)));
    }
}
