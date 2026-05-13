package com.yoyuzh.identity.access.internal.web;

import com.yoyuzh.identity.access.internal.application.AvatarDownloadResult;
import com.yoyuzh.identity.access.internal.application.AuthService;
import com.yoyuzh.identity.access.api.UpdateUserAvatarRequest;
import com.yoyuzh.identity.access.api.UpdateUserPasswordRequest;
import com.yoyuzh.identity.access.api.UpdateUserProfileRequest;
import com.yoyuzh.identity.access.api.UpdateUserSettingsRequest;
import com.yoyuzh.shared.kernel.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Operation(summary = "获取当前用户容量")
    @GetMapping("/capacity")
    public ApiResponse<?> capacity(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(authService.getCapacity(userDetails.getUsername()));
    }

    @Operation(summary = "获取当前用户设置")
    @GetMapping("/settings")
    public ApiResponse<?> settings(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(authService.getSettings(userDetails.getUsername()));
    }

    @Operation(summary = "获取当前用户 WebDAV 凭据状态")
    @GetMapping("/webdav-credential")
    public ApiResponse<?> webDavCredential(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(authService.getWebDavCredential(userDetails.getUsername()));
    }

    @Operation(summary = "生成或重置当前用户 WebDAV 凭据")
    @PostMapping("/webdav-credential")
    public ApiResponse<?> issueWebDavCredential(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(authService.issueWebDavCredential(userDetails.getUsername()));
    }

    @Operation(summary = "更新当前用户设置")
    @PutMapping("/settings")
    public ApiResponse<?> updateSettings(@AuthenticationPrincipal UserDetails userDetails,
                                         @Valid @RequestBody UpdateUserSettingsRequest request) {
        return ApiResponse.success(authService.updateSettings(userDetails.getUsername(), request));
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
        AvatarDownloadResult result = authService.getAvatarContent(userDetails.getUsername());
        if (result.redirect()) {
            return ResponseEntity.status(302)
                    .location(URI.create(result.redirectUrl()))
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + URLEncoder.encode(result.filename(), StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.body());
    }
}
