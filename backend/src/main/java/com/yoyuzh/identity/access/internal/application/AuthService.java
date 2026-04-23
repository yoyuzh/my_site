package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.AuthResponse;
import com.yoyuzh.identity.access.api.DevLoginRoleResolver;
import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityStorageUsageQuery;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import com.yoyuzh.identity.access.api.LoginRequest;
import com.yoyuzh.identity.access.api.LoginAdmissionPolicy;
import com.yoyuzh.identity.access.api.PasswordChangeAttempt;
import com.yoyuzh.identity.access.api.PasswordChangePolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAdmissionPolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAttempt;
import com.yoyuzh.identity.access.api.RegistrationAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegistrationAttempt;
import com.yoyuzh.identity.access.api.RegisterRequest;
import com.yoyuzh.identity.access.api.UpdateUserAvatarRequest;
import com.yoyuzh.identity.access.api.UpdateUserPasswordRequest;
import com.yoyuzh.identity.access.api.UpdateUserProfileRequest;
import com.yoyuzh.identity.access.api.UserCapacityResponse;
import com.yoyuzh.identity.access.api.UserProfileResponse;
import com.yoyuzh.identity.access.api.UserSettingsResponse;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String AVATAR_PATH = "/.avatar";
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceBootstrapApi workspaceBootstrapApi;
    private final FileContentStorage fileContentStorage;
    private final RegistrationAdmissionPolicy registrationAdmissionPolicy;
    private final DevLoginRoleResolver devLoginRoleResolver;
    private final ProfileUpdateAdmissionPolicy profileUpdateAdmissionPolicy;
    private final LoginAdmissionPolicy loginAdmissionPolicy;
    private final PasswordChangePolicy passwordChangePolicy;
    private final IdentityCredentialIssuer identityCredentialIssuer;
    private final IdentityStorageUsageQuery identityStorageUsageQuery;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return register(request, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, IdentityClientType clientType) {
        registrationAdmissionPolicy.assertAllowed(
                new RegistrationAttempt(
                        request.username(),
                        request.email(),
                        request.phoneNumber(),
                        request.inviteCode()));

        User user = new User();
        user.setUsername(request.username());
        user.setDisplayName(request.username());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setPreferredLanguage("zh-CN");
        User saved = userRepository.save(user);
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(saved));
        return toAuthResponse(identityCredentialIssuer.issueFresh(saved.getId(), clientType));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, IdentityClientType clientType) {
        loginAdmissionPolicy.assertAllowed(request.username(), request.password());

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(user));
        return toAuthResponse(identityCredentialIssuer.issueFresh(user.getId(), clientType));
    }

    @Transactional
    public AuthResponse devLogin(String username) {
        return devLogin(username, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse devLogin(String username, IdentityClientType clientType) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isEmpty()) {
            candidate = "1";
        }

        final String finalCandidate = candidate;
        UserRole desiredRole = toUserRole(devLoginRoleResolver.resolveRoleForUsername(finalCandidate));
        User user = userRepository.findByUsername(finalCandidate).map(existing -> {
            if (existing.getRole() != desiredRole) {
                existing.setRole(desiredRole);
                return userRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            User created = new User();
            created.setUsername(finalCandidate);
            created.setDisplayName(finalCandidate);
            created.setEmail(finalCandidate + "@dev.local");
            created.setPasswordHash(passwordEncoder.encode("1"));
            created.setRole(desiredRole);
            created.setPreferredLanguage("zh-CN");
            return userRepository.save(created);
        });
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(user));
        return toAuthResponse(identityCredentialIssuer.issueFresh(user.getId(), clientType));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        return refresh(refreshToken, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, IdentityClientType defaultClientType) {
        return toAuthResponse(identityCredentialIssuer.refresh(refreshToken, defaultClientType));
    }

    public UserProfileResponse getProfile(String username) {
        return toProfile(findUserByUsername(username));
    }

    public UserCapacityResponse getCapacity(String username) {
        User user = findUserByUsername(username);
        long totalBytes = user.getStorageQuotaBytes();
        long usedBytes = identityStorageUsageQuery.usedStorageBytes(user.getId());
        long availableBytes = Math.max(0L, totalBytes - usedBytes);
        return new UserCapacityResponse(totalBytes, usedBytes, availableBytes, user.getMaxUploadSizeBytes());
    }

    public UserSettingsResponse getSettings(String username) {
        User user = findUserByUsername(username);
        return new UserSettingsResponse(
                user.getDisplayName(),
                user.getPreferredLanguage(),
                "system",
                false
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateUserProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        String nextEmail = request.email().trim();
        String nextPhoneNumber = request.phoneNumber().trim();
        profileUpdateAdmissionPolicy.assertAllowed(
                new ProfileUpdateAttempt(user.getEmail(), user.getPhoneNumber(), nextEmail, nextPhoneNumber));

        user.setDisplayName(request.displayName().trim());
        user.setEmail(nextEmail);
        user.setPhoneNumber(nextPhoneNumber);
        user.setBio(normalizeOptionalText(request.bio()));
        user.setPreferredLanguage(normalizePreferredLanguage(request.preferredLanguage()));
        return toProfile(userRepository.save(user));
    }

    @Transactional
    public AuthResponse changePassword(String username, UpdateUserPasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        return toAuthResponse(passwordChangePolicy.changePassword(
                user.getId(),
                new PasswordChangeAttempt(request.currentPassword(), request.newPassword(), IdentityClientType.DESKTOP)));
    }

    public InitiateUploadResponse initiateAvatarUpload(String username, UpdateUserAvatarRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        validateAvatarUpload(request.filename(), request.contentType(), request.size());
        String storageName = normalizeAvatarStorageName(request.storageName(), request.filename(), request.contentType());

        var preparedUpload = fileContentStorage.prepareUpload(
                user.getId(),
                AVATAR_PATH,
                storageName,
                request.contentType(),
                request.size()
        );

        String uploadUrl = preparedUpload.direct()
                ? preparedUpload.uploadUrl()
                : "/api/user/avatar/upload?storageName=" + URLEncoder.encode(storageName, StandardCharsets.UTF_8);

        return new InitiateUploadResponse(
                preparedUpload.direct(),
                uploadUrl,
                preparedUpload.direct() ? preparedUpload.method() : "POST",
                preparedUpload.direct() ? preparedUpload.headers() : java.util.Map.of(),
                storageName
        );
    }

    public void uploadAvatar(String username, String storageName, MultipartFile file) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        String normalizedStorageName = normalizeAvatarStorageName(storageName, file.getOriginalFilename(), file.getContentType());
        validateAvatarUpload(file.getOriginalFilename(), file.getContentType(), file.getSize());
        fileContentStorage.upload(user.getId(), AVATAR_PATH, normalizedStorageName, file);
    }

    @Transactional
    public UserProfileResponse completeAvatarUpload(String username, UpdateUserAvatarRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        validateAvatarUpload(request.filename(), request.contentType(), request.size());
        String storageName = normalizeAvatarStorageName(request.storageName(), request.filename(), request.contentType());

        fileContentStorage.completeUpload(user.getId(), AVATAR_PATH, storageName, request.contentType(), request.size());

        String previousStorageName = user.getAvatarStorageName();
        if (StringUtils.hasText(previousStorageName) && !previousStorageName.equals(storageName)) {
            fileContentStorage.deleteFile(user.getId(), AVATAR_PATH, previousStorageName);
        }

        user.setAvatarStorageName(storageName);
        user.setAvatarContentType(request.contentType());
        user.setAvatarUpdatedAt(LocalDateTime.now());
        return toProfile(userRepository.save(user));
    }

    public ResponseEntity<?> getAvatarContent(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        if (!StringUtils.hasText(user.getAvatarStorageName())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "头像不存在");
        }

        String downloadName = buildAvatarDownloadName(user.getAvatarStorageName(), user.getAvatarContentType());
        if (fileContentStorage.supportsDirectDownload()) {
            return ResponseEntity.status(302)
                    .location(URI.create(fileContentStorage.createDownloadUrl(
                            user.getId(),
                            AVATAR_PATH,
                            user.getAvatarStorageName(),
                            downloadName
                    )))
                    .build();
        }

        byte[] content = fileContentStorage.readFile(user.getId(), AVATAR_PATH, user.getAvatarStorageName());
        String contentType = StringUtils.hasText(user.getAvatarContentType())
                ? user.getAvatarContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + URLEncoder.encode(downloadName, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getPreferredLanguage(),
                buildAvatarUrl(user),
                toIdentityRoleName(user.getRole()),
                user.getCreatedAt(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
    }

    private AuthResponse toAuthResponse(IssuedAuthCredentials issuedAuthCredentials) {
        return AuthResponse.issued(
                issuedAuthCredentials.accessToken(),
                issuedAuthCredentials.refreshToken(),
                toProfile(issuedAuthCredentials.user()));
    }

    private UserProfileResponse toProfile(IdentityUserSnapshot user) {
        return new UserProfileResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                user.phoneNumber(),
                user.bio(),
                user.preferredLanguage(),
                buildAvatarUrl(user),
                user.role(),
                user.createdAt(),
                user.storageQuotaBytes(),
                user.maxUploadSizeBytes()
        );
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserRole toUserRole(IdentityRoleName roleName) {
        if (roleName == null) {
            return UserRole.USER;
        }
        return switch (roleName) {
            case ADMIN -> UserRole.ADMIN;
            case MODERATOR -> UserRole.MODERATOR;
            case USER -> UserRole.USER;
        };
    }

    private IdentityRoleName toIdentityRoleName(UserRole role) {
        if (role == null) {
            return IdentityRoleName.USER;
        }
        return IdentityRoleName.valueOf(role.name());
    }

    private String normalizePreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null || preferredLanguage.trim().isEmpty()) {
            return "zh-CN";
        }
        return preferredLanguage.trim();
    }

    private void validateAvatarUpload(String filename, String contentType, long size) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "头像文件名不能为空");
        }
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "头像仅支持图片文件");
        }
        if (size <= 0 || size > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.UNKNOWN, "头像大小不能超过 5MB");
        }
    }

    private String normalizeAvatarStorageName(String requestedStorageName, String filename, String contentType) {
        String candidate = StringUtils.hasText(requestedStorageName)
                ? requestedStorageName.trim()
                : "avatar-" + UUID.randomUUID() + resolveAvatarExtension(filename, contentType);
        candidate = candidate.replace("\\", "/");
        if (candidate.contains("/")) {
            candidate = candidate.substring(candidate.lastIndexOf('/') + 1);
        }
        if (!StringUtils.hasText(candidate)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "头像文件名不合法");
        }
        return candidate;
    }

    private String resolveAvatarExtension(String filename, String contentType) {
        if (StringUtils.hasText(filename)) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
                if (extension.matches("\\.[a-z0-9]{1,8}")) {
                    return extension;
                }
            }
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private String buildAvatarUrl(User user) {
        if (!StringUtils.hasText(user.getAvatarStorageName())) {
            return null;
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return fileContentStorage.createDownloadUrl(
                    user.getId(),
                    AVATAR_PATH,
                    user.getAvatarStorageName(),
                    buildAvatarDownloadName(user.getAvatarStorageName(), user.getAvatarContentType())
            );
        }

        long version = user.getAvatarUpdatedAt() == null ? 0L : user.getAvatarUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "/user/avatar/content?v=" + version;
    }

    private String buildAvatarUrl(IdentityUserSnapshot user) {
        if (!StringUtils.hasText(user.avatarStorageName())) {
            return null;
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return fileContentStorage.createDownloadUrl(
                    user.id(),
                    AVATAR_PATH,
                    user.avatarStorageName(),
                    buildAvatarDownloadName(user.avatarStorageName(), user.avatarContentType())
            );
        }

        long version = user.avatarUpdatedAt() == null
                ? 0L
                : user.avatarUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "/user/avatar/content?v=" + version;
    }

    private String buildAvatarDownloadName(String storageName, String contentType) {
        if (StringUtils.hasText(storageName) && storageName.contains(".")) {
            return storageName;
        }
        return "avatar" + resolveAvatarExtension(storageName, contentType == null ? "image/png" : contentType);
    }

    private WorkspaceUserContext workspaceUser(User user) {
        return new WorkspaceUserContext(
                user.getId(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
