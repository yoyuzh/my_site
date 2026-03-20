package com.yoyuzh.auth;

import com.yoyuzh.auth.dto.AuthResponse;
import com.yoyuzh.auth.dto.LoginRequest;
import com.yoyuzh.auth.dto.RegisterRequest;
import com.yoyuzh.auth.dto.UpdateUserAvatarRequest;
import com.yoyuzh.auth.dto.UpdateUserPasswordRequest;
import com.yoyuzh.auth.dto.UpdateUserProfileRequest;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.files.FileService;
import com.yoyuzh.files.InitiateUploadResponse;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private FileService fileService;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private RegistrationInviteService registrationInviteService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserWithEncryptedPassword() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass1!",
                "invite-code"
        );
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("alice"), anyString())).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().phoneNumber()).isEqualTo("13800138000");
        verify(registrationInviteService).consumeInviteCode("invite-code");
        verify(passwordEncoder).encode("StrongPass1!");
        verify(fileService).ensureDefaultDirectories(any(User.class));
    }

    @Test
    void shouldRejectDuplicateUsernameOnRegister() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass1!",
                "invite-code"
        );
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void shouldRejectDuplicatePhoneNumberOnRegister() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass1!",
                "invite-code"
        );
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号已存在");
    }

    @Test
    void shouldRejectInvalidInviteCodeOnRegister() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "13800138000",
                "StrongPass1!",
                "StrongPass1!",
                "wrong-code"
        );
        var invalidInviteCode = new BusinessException(com.yoyuzh.common.ErrorCode.PERMISSION_DENIED, "邀请码错误");
        org.mockito.Mockito.doThrow(invalidInviteCode)
                .when(registrationInviteService)
                .consumeInviteCode("wrong-code");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邀请码错误");
    }

    @Test
    void shouldLoginAndReturnToken() {
        LoginRequest request = new LoginRequest("alice", "plain-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("encoded-password");
        user.setCreatedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("alice"), anyString())).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("alice", "plain-password"));
        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        verify(fileService).ensureDefaultDirectories(user);
    }

    @Test
    void shouldRotateRefreshTokenAndReturnNewCredentials() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setCreatedAt(LocalDateTime.now());
        when(refreshTokenService.rotateRefreshToken("old-refresh"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(user, "new-refresh"));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("alice"), anyString())).thenReturn("new-access");

        AuthResponse response = authService.refresh("old-refresh");

        assertThat(response.token()).isEqualTo("new-access");
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void shouldThrowBusinessExceptionWhenAuthenticationFails() {
        LoginRequest request = new LoginRequest("alice", "wrong-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void shouldRejectBannedUserLogin() {
        LoginRequest request = new LoginRequest("alice", "plain-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被封禁");
    }

    @Test
    void shouldCreateDefaultDirectoriesForDevLoginUser() {
        when(userRepository.findByUsername("demo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });
        when(jwtTokenProvider.generateAccessToken(eq(9L), eq("demo"), anyString())).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.devLogin("demo");

        assertThat(response.user().username()).isEqualTo("demo");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(fileService).ensureDefaultDirectories(any(User.class));
    }

    @Test
    void shouldUpdateCurrentUserProfile() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPhoneNumber("13800138000");
        user.setBio("old bio");
        user.setPreferredLanguage("zh-CN");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Alicia",
                "newalice@example.com",
                "13900139000",
                "new bio",
                "en-US"
        );

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("newalice@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("13900139000")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        var response = authService.updateProfile("alice", request);

        assertThat(response.displayName()).isEqualTo("Alicia");
        assertThat(response.email()).isEqualTo("newalice@example.com");
        assertThat(response.phoneNumber()).isEqualTo("13900139000");
        assertThat(response.bio()).isEqualTo("new bio");
        assertThat(response.preferredLanguage()).isEqualTo("en-US");
    }

    @Test
    void shouldChangePasswordAndIssueFreshTokens() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPreferredLanguage("zh-CN");
        user.setRole(UserRole.USER);
        user.setPasswordHash("encoded-old");
        user.setCreatedAt(LocalDateTime.now());

        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest("OldPass1!", "NewPass1!A");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!A")).thenReturn("encoded-new");
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("alice"), anyString())).thenReturn("new-access");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("new-refresh");

        AuthResponse response = authService.changePassword("alice", request);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(passwordEncoder).encode("NewPass1!A");
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("encoded-old");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1!", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("alice", new UpdateUserPasswordRequest("WrongPass1!", "NewPass1!A")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前密码错误");
    }

    @Test
    void shouldInitiateAvatarUploadThroughStorage() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(fileContentStorage.prepareUpload(eq(1L), eq("/.avatar"), any(), eq("image/png"), eq(2048L)))
                .thenReturn(new PreparedUpload(true, "https://upload.example.com/avatar", "PUT", java.util.Map.of("Content-Type", "image/png"), "avatar-generated.png"));

        InitiateUploadResponse response = authService.initiateAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("face.png", "image/png", 2048L, "avatar-generated.png")
        );

        assertThat(response.direct()).isTrue();
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com/avatar");
        assertThat(response.storageName()).endsWith(".png");
    }

    @Test
    void shouldCompleteAvatarUploadAndReplacePreviousAvatar() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPreferredLanguage("zh-CN");
        user.setRole(UserRole.USER);
        user.setAvatarStorageName("old-avatar.png");
        user.setAvatarContentType("image/png");
        user.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createDownloadUrl(anyLong(), eq("/.avatar"), eq("new-avatar.webp"), any()))
                .thenReturn("https://cdn.example.com/avatar.webp");
        when(userRepository.save(user)).thenReturn(user);

        var response = authService.completeAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("face.webp", "image/webp", 4096L, "new-avatar.webp")
        );

        verify(fileContentStorage).completeUpload(1L, "/.avatar", "new-avatar.webp", "image/webp", 4096L);
        verify(fileContentStorage).deleteFile(1L, "/.avatar", "old-avatar.png");
        assertThat(response.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.webp");
    }
}
