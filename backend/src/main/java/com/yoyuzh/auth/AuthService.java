package com.yoyuzh.auth;

import com.yoyuzh.auth.dto.AuthResponse;
import com.yoyuzh.auth.dto.LoginRequest;
import com.yoyuzh.auth.dto.RegisterRequest;
import com.yoyuzh.auth.dto.UpdateUserAvatarRequest;
import com.yoyuzh.auth.dto.UpdateUserPasswordRequest;
import com.yoyuzh.auth.dto.UpdateUserProfileRequest;
import com.yoyuzh.auth.dto.UserProfileResponse;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.FileService;
import com.yoyuzh.files.InitiateUploadResponse;
import com.yoyuzh.files.storage.FileContentStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final FileService fileService;
    private final FileContentStorage fileContentStorage;
    private final RegistrationInviteService registrationInviteService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "用户名已存在");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "邮箱已存在");
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "手机号已存在");
        }

        registrationInviteService.consumeInviteCode(request.inviteCode());

        User user = new User();
        user.setUsername(request.username());
        user.setDisplayName(request.username());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setPreferredLanguage("zh-CN");
        User saved = userRepository.save(user);
        fileService.ensureDefaultDirectories(saved);
        return issueFreshTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException ex) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "账号已被封禁");
        } catch (BadCredentialsException ex) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户名或密码错误");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        fileService.ensureDefaultDirectories(user);
        return issueFreshTokens(user);
    }

    @Transactional
    public AuthResponse devLogin(String username) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isEmpty()) {
            candidate = "1";
        }

        final String finalCandidate = candidate;
        User user = userRepository.findByUsername(finalCandidate).orElseGet(() -> {
            User created = new User();
            created.setUsername(finalCandidate);
            created.setDisplayName(finalCandidate);
            created.setEmail(finalCandidate + "@dev.local");
            created.setPasswordHash(passwordEncoder.encode("1"));
            created.setRole(UserRole.USER);
            created.setPreferredLanguage("zh-CN");
            return userRepository.save(created);
        });
        fileService.ensureDefaultDirectories(user);
        return issueFreshTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotateRefreshToken(refreshToken);
        return issueTokens(rotated.user(), rotated.refreshToken());
    }

    public UserProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateUserProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        String nextEmail = request.email().trim();
        if (!user.getEmail().equalsIgnoreCase(nextEmail) && userRepository.existsByEmail(nextEmail)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "邮箱已存在");
        }
        String nextPhoneNumber = request.phoneNumber().trim();
        if (!nextPhoneNumber.equals(user.getPhoneNumber()) && userRepository.existsByPhoneNumber(nextPhoneNumber)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "手机号已存在");
        }

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

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "当前密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return issueFreshTokens(user);
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
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private AuthResponse issueFreshTokens(User user) {
        refreshTokenService.revokeAllForUser(user.getId());
        return issueTokens(user, refreshTokenService.issueRefreshToken(user));
    }

    private AuthResponse issueTokens(User user, String refreshToken) {
        User sessionUser = rotateActiveSession(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                sessionUser.getId(),
                sessionUser.getUsername(),
                sessionUser.getActiveSessionId()
        );
        return AuthResponse.issued(accessToken, refreshToken, toProfile(sessionUser));
    }

    private User rotateActiveSession(User user) {
        user.setActiveSessionId(UUID.randomUUID().toString());
        return userRepository.save(user);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private String buildAvatarDownloadName(String storageName, String contentType) {
        if (StringUtils.hasText(storageName) && storageName.contains(".")) {
            return storageName;
        }
        return "avatar" + resolveAvatarExtension(storageName, contentType == null ? "image/png" : contentType);
    }
}
