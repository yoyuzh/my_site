package com.yoyuzh.auth;

import com.yoyuzh.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import com.yoyuzh.auth.dto.UpdateUserAvatarRequest;
import com.yoyuzh.auth.dto.UpdateUserPasswordRequest;
import com.yoyuzh.auth.dto.UpdateUserProfileRequest;

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

    @Operation(summary = "更新用户资料")
    @PutMapping("/profile")
    public ApiResponse<?> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                        @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(userDetails.getUsername(), request));
    }

    @Operation(summary = "修改当前用户密码")
    @PostMapping("/password")
    public ApiResponse<?> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                         @Valid @RequestBody UpdateUserPasswordRequest request) {
        return ApiResponse.success(authService.changePassword(userDetails.getUsername(), request));
    }

    @Operation(summary = "初始化头像上传")
    @PostMapping("/avatar/upload/initiate")
    public ApiResponse<?> initiateAvatarUpload(@AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody UpdateUserAvatarRequest request) {
        return ApiResponse.success(authService.initiateAvatarUpload(userDetails.getUsername(), request));
    }

    @Operation(summary = "代理上传头像")
    @PostMapping("/avatar/upload")
    public ApiResponse<?> uploadAvatar(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam String storageName,
                                       @RequestPart("file") MultipartFile file) {
        authService.uploadAvatar(userDetails.getUsername(), storageName, file);
        return ApiResponse.success();
    }

    @Operation(summary = "完成头像上传")
    @PostMapping("/avatar/upload/complete")
    public ApiResponse<?> completeAvatarUpload(@AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody UpdateUserAvatarRequest request) {
        return ApiResponse.success(authService.completeAvatarUpload(userDetails.getUsername(), request));
    }

    @Operation(summary = "获取当前用户头像")
    @GetMapping("/avatar/content")
    public ResponseEntity<?> avatarContent(@AuthenticationPrincipal UserDetails userDetails) {
        return authService.getAvatarContent(userDetails.getUsername());
    }
}
