package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.*;
import com.yoyuzh.identity.access.internal.application.*;
import com.yoyuzh.identity.access.internal.domain.*;
import com.yoyuzh.identity.access.internal.infra.*;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;

import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import com.yoyuzh.identity.access.api.AuthResponse;
import com.yoyuzh.identity.access.api.DevLoginRoleResolver;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import com.yoyuzh.identity.access.api.LoginRequest;
import com.yoyuzh.identity.access.api.LoginAdmissionPolicy;
import com.yoyuzh.identity.access.api.PasswordChangeAttempt;
import com.yoyuzh.identity.access.api.PasswordChangePolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegistrationAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegisterRequest;
import com.yoyuzh.identity.access.api.UpdateUserAvatarRequest;
import com.yoyuzh.identity.access.api.UpdateUserPasswordRequest;
import com.yoyuzh.identity.access.api.UpdateUserProfileRequest;
import com.yoyuzh.identity.access.internal.domain.RandomIdentitySessionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAdmissionPolicy loginAdmissionPolicy;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthTokenInvalidationService authTokenInvalidationService;

    @Mock
    private WorkspaceBootstrapApi workspaceBootstrapApi;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private RegistrationAdmissionPolicy registrationAdmissionPolicy;

    @Mock
    private DevLoginRoleResolver devLoginRoleResolver;

    @Mock
    private ProfileUpdateAdmissionPolicy profileUpdateAdmissionPolicy;

    @Mock
    private PasswordChangePolicy passwordChangePolicy;

    @Mock
    private IdentityCredentialIssuer identityCredentialIssuer;

    @Mock
    private IdentityStorageUsageQuery identityStorageUsageQuery;

    @Spy
    private AuthSessionPolicy authSessionPolicy = new AuthSessionPolicy(new RandomIdentitySessionPolicy());

    private AvatarService avatarService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        avatarService = new AvatarService(userRepository, fileContentStorage);
        ReflectionTestUtils.setField(authService, "avatarService", avatarService);
    }

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
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });
        when(identityCredentialIssuer.issueFresh(anyLong(), eq(IdentityClientType.DESKTOP)))
                .thenAnswer(invocation -> {
                    Long issuedUserId = invocation.getArgument(0);
                    User issuedUser = new User();
                    issuedUser.setId(issuedUserId);
                    issuedUser.setUsername("alice");
                    issuedUser.setDisplayName("alice");
                    issuedUser.setEmail("alice@example.com");
                    issuedUser.setPhoneNumber("13800138000");
                    issuedUser.setPreferredLanguage("zh-CN");
                    issuedUser.setCreatedAt(LocalDateTime.now());
                    return new IssuedAuthCredentials(snapshot(issuedUser), "access-token", "refresh-token");
                });

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().phoneNumber()).isEqualTo("13800138000");
        verify(registrationAdmissionPolicy).assertAllowed(any());
        verify(passwordEncoder).encode("StrongPass1!");
        verify(workspaceBootstrapApi).ensureDefaultDirectories(argThat(user -> user.userId().equals(1L)));
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
        org.mockito.Mockito.doThrow(new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.UNKNOWN, "用户名已存在"))
                .when(registrationAdmissionPolicy)
                .assertAllowed(any());

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
        org.mockito.Mockito.doThrow(new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.UNKNOWN, "手机号已存在"))
                .when(registrationAdmissionPolicy)
                .assertAllowed(any());

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
        var invalidInviteCode = new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.PERMISSION_DENIED, "邀请码错误");
        org.mockito.Mockito.doThrow(invalidInviteCode)
                .when(registrationAdmissionPolicy)
                .assertAllowed(any());

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
        when(identityCredentialIssuer.issueFresh(user.getId(), IdentityClientType.DESKTOP))
                .thenReturn(new IssuedAuthCredentials(snapshot(user), "access-token", "refresh-token"));

        AuthResponse response = authService.login(request);

        verify(loginAdmissionPolicy).assertAllowed("alice", "plain-password");
        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        verify(workspaceBootstrapApi).ensureDefaultDirectories(argThat(context -> context.userId().equals(user.getId())));
    }

    @Test
    void shouldRotateRefreshTokenAndReturnNewCredentials() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("alice");
        user.setPreferredLanguage("zh-CN");
        user.setCreatedAt(LocalDateTime.now());
        when(identityCredentialIssuer.refresh("old-refresh", IdentityClientType.DESKTOP))
                .thenReturn(new IssuedAuthCredentials(snapshot(user), "new-access", "new-refresh"));

        AuthResponse response = authService.refresh("old-refresh");

        assertThat(response.token()).isEqualTo("new-access");
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void shouldThrowBusinessExceptionWhenAuthenticationFails() {
        LoginRequest request = new LoginRequest("alice", "wrong-password");
        org.mockito.Mockito.doThrow(
                        new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.NOT_LOGGED_IN, "用户名或密码错误"))
                .when(loginAdmissionPolicy)
                .assertAllowed("alice", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void shouldRejectBannedUserLogin() {
        LoginRequest request = new LoginRequest("alice", "plain-password");
        org.mockito.Mockito.doThrow(
                        new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.PERMISSION_DENIED, "账号已被封禁"))
                .when(loginAdmissionPolicy)
                .assertAllowed("alice", "plain-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被封禁");
    }

    @Test
    void shouldCreateDefaultDirectoriesForDevLoginUser() {
        when(devLoginRoleResolver.resolveRoleForUsername("demo")).thenReturn(IdentityRoleName.USER);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });
        when(identityCredentialIssuer.issueFresh(anyLong(), eq(IdentityClientType.DESKTOP)))
                .thenAnswer(invocation -> {
                    Long issuedUserId = invocation.getArgument(0);
                    User issuedUser = new User();
                    issuedUser.setId(issuedUserId);
                    issuedUser.setUsername("demo");
                    issuedUser.setDisplayName("demo");
                    issuedUser.setEmail("demo@dev.local");
                    issuedUser.setRole(UserRole.USER);
                    issuedUser.setPreferredLanguage("zh-CN");
                    issuedUser.setCreatedAt(LocalDateTime.now());
                    return new IssuedAuthCredentials(snapshot(issuedUser), "access-token", "refresh-token");
                });

        AuthResponse response = authService.devLogin("demo");

        assertThat(response.user().username()).isEqualTo("demo");
        assertThat(response.user().role()).isEqualTo(IdentityRoleName.USER);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(workspaceBootstrapApi).ensureDefaultDirectories(any(WorkspaceUserContext.class));
    }

    @Test
    void shouldUpgradeAdminDevLoginUserToAdminRole() {
        when(devLoginRoleResolver.resolveRoleForUsername("admin")).thenReturn(IdentityRoleName.ADMIN);
        User existing = new User();
        existing.setId(18L);
        existing.setUsername("admin");
        existing.setDisplayName("admin");
        existing.setEmail("admin@dev.local");
        existing.setRole(UserRole.USER);
        existing.setPreferredLanguage("zh-CN");
        existing.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(identityCredentialIssuer.issueFresh(existing.getId(), IdentityClientType.DESKTOP))
                .thenAnswer(invocation -> new IssuedAuthCredentials(snapshot(existing), "admin-access-token", "admin-refresh-token"));

        AuthResponse response = authService.devLogin("admin");

        assertThat(response.user().role()).isEqualTo(IdentityRoleName.ADMIN);
        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
        verify(workspaceBootstrapApi).ensureDefaultDirectories(argThat(context -> context.userId().equals(existing.getId())));
    }

    @Test
    void shouldUpgradeOperatorDevLoginUserToModeratorRole() {
        when(devLoginRoleResolver.resolveRoleForUsername("operator")).thenReturn(IdentityRoleName.MODERATOR);
        User existing = new User();
        existing.setId(19L);
        existing.setUsername("operator");
        existing.setDisplayName("operator");
        existing.setEmail("operator@dev.local");
        existing.setRole(UserRole.USER);
        existing.setPreferredLanguage("zh-CN");
        existing.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(identityCredentialIssuer.issueFresh(existing.getId(), IdentityClientType.DESKTOP))
                .thenAnswer(invocation -> new IssuedAuthCredentials(snapshot(existing), "operator-access-token", "operator-refresh-token"));

        AuthResponse response = authService.devLogin("operator");

        assertThat(response.user().role()).isEqualTo(IdentityRoleName.MODERATOR);
        assertThat(existing.getRole()).isEqualTo(UserRole.MODERATOR);
        verify(workspaceBootstrapApi).ensureDefaultDirectories(argThat(context -> context.userId().equals(existing.getId())));
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
        when(userRepository.save(user)).thenReturn(user);

        var response = authService.updateProfile("alice", request);

        verify(profileUpdateAdmissionPolicy).assertAllowed(any());
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
        when(passwordChangePolicy.changePassword(eq(user.getId()), argThat(attempt ->
                attempt.currentPassword().equals("OldPass1!")
                        && attempt.newPassword().equals("NewPass1!A")
                        && attempt.clientType() == IdentityClientType.DESKTOP)))
                .thenReturn(new IssuedAuthCredentials(snapshot(user), "new-access", "new-refresh"));

        AuthResponse response = authService.changePassword("alice", request);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(passwordChangePolicy).changePassword(
                eq(user.getId()),
                eq(new PasswordChangeAttempt("OldPass1!", "NewPass1!A", IdentityClientType.DESKTOP)));
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("encoded-old");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordChangePolicy.changePassword(
                eq(user.getId()),
                eq(new PasswordChangeAttempt("WrongPass1!", "NewPass1!A", IdentityClientType.DESKTOP))))
                .thenThrow(new BusinessException(com.yoyuzh.shared.kernel.ErrorCode.UNKNOWN, "当前密码错误"));

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

    @Test
    void shouldInitiateProxyAvatarUploadWhenStorageDoesNotReturnDirectUpload() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(fileContentStorage.prepareUpload(eq(1L), eq("/.avatar"), anyString(), eq("image/webp"), eq(2048L)))
                .thenAnswer(invocation -> new PreparedUpload(false, null, null, java.util.Map.of(), invocation.getArgument(2)));

        InitiateUploadResponse response = authService.initiateAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("face", "image/webp", 2048L, "  ")
        );

        assertThat(response.direct()).isFalse();
        assertThat(response.method()).isEqualTo("POST");
        assertThat(response.headers()).isEmpty();
        assertThat(response.storageName()).startsWith("avatar-").endsWith(".webp");
        assertThat(response.uploadUrl()).startsWith("/api/user/avatar/upload?storageName=avatar-");
        assertThat(response.uploadUrl()).endsWith(".webp");
    }

    @Test
    void shouldUploadAvatarWithSanitizedStorageName() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "face.gif",
                "image/gif",
                "gif".getBytes(StandardCharsets.UTF_8)
        );

        authService.uploadAvatar("alice", "nested\\avatar.gif", file);

        verify(fileContentStorage).upload(1L, "/.avatar", "avatar.gif", file);
    }

    @Test
    void shouldCompleteAvatarUploadWithoutDeletingWhenStorageNameIsUnchanged() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPreferredLanguage("zh-CN");
        user.setRole(UserRole.USER);
        user.setAvatarStorageName("avatar.png");
        user.setAvatarContentType("image/png");
        user.setCreatedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var response = authService.completeAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("avatar.png", "image/png", 1024L, "avatar.png")
        );

        verify(fileContentStorage).completeUpload(1L, "/.avatar", "avatar.png", "image/png", 1024L);
        verify(fileContentStorage, never()).deleteFile(anyLong(), anyString(), anyString());
        assertThat(response.avatarUrl()).startsWith("/user/avatar/content?v=");
    }

    @Test
    void shouldReadAvatarContentInlineWhenStorageCannotRedirect() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatarStorageName("avatar-storage");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(false);
        when(fileContentStorage.readFile(1L, "/.avatar", "avatar-storage"))
                .thenReturn("avatar".getBytes(StandardCharsets.UTF_8));

        AvatarDownloadResult response = authService.getAvatarContent("alice");

        assertThat(response.redirect()).isFalse();
        assertThat(response.contentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(response.filename()).isEqualTo("avatar.png");
        assertThat(response.body()).isEqualTo("avatar".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldRedirectAvatarContentWhenStorageSupportsDirectDownload() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatarStorageName("avatar-storage");
        user.setAvatarContentType("image/jpeg");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(fileContentStorage.supportsDirectDownload()).thenReturn(true);
        when(fileContentStorage.createDownloadUrl(1L, "/.avatar", "avatar-storage", "avatar.jpg"))
                .thenReturn("https://cdn.example.com/avatar.jpg");

        AvatarDownloadResult response = authService.getAvatarContent("alice");

        assertThat(response.redirect()).isTrue();
        assertThat(response.redirectUrl()).isEqualTo("https://cdn.example.com/avatar.jpg");
        assertThat(response.body()).isNull();
    }

    @Test
    void shouldRejectAvatarContentWhenUserHasNoAvatar() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.getAvatarContent("alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("头像不存在");
    }

    @Test
    void shouldRejectInvalidAvatarUploadInputs() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.initiateAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("", "image/png", 1024L, null)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("头像文件名不能为空");

        assertThatThrownBy(() -> authService.initiateAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("face.txt", "text/plain", 1024L, null)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("头像仅支持图片文件");

        assertThatThrownBy(() -> authService.initiateAvatarUpload(
                "alice",
                new UpdateUserAvatarRequest("face.png", "image/png", 0L, null)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("头像大小不能超过 5MB");
    }

    private static IdentityUserSnapshot snapshot(User user) {
        return new IdentityUserSnapshot(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getPreferredLanguage(),
                user.getAvatarStorageName(),
                user.getAvatarContentType(),
                user.getAvatarUpdatedAt(),
                user.getRole() == null ? IdentityRoleName.USER : IdentityRoleName.valueOf(user.getRole().name()),
                user.getCreatedAt(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
