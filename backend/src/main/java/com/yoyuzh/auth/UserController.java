package com.yoyuzh.auth;

import com.yoyuzh.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(summary = "获取用户信息")
    @GetMapping("/profile")
    public ApiResponse<?> profile(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(authService.getProfile(userDetails.getUsername()));
    }
}
