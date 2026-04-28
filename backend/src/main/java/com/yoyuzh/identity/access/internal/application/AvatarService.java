package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.identity.access.api.UpdateUserAvatarRequest;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final String AVATAR_PATH = "/.avatar";
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024L;

    private final UserRepository userRepository;
    private final FileContentStorage fileContentStorage;

    public InitiateUploadResponse initiateAvatarUpload(String username, UpdateUserAvatarRequest request) {
        User user = findUserByUsername(username);

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
        String method = preparedUpload.direct() ? preparedUpload.method() : "POST";
        return new InitiateUploadResponse(
                preparedUpload.direct(),
                uploadUrl,
                method,
                preparedUpload.direct() ? preparedUpload.headers() : Map.of(),
                storageName
        );
    }

    public void uploadAvatar(String username, String storageName, MultipartFile file) {
        User user = findUserByUsername(username);

        String normalizedStorageName = normalizeAvatarStorageName(storageName, file.getOriginalFilename(), file.getContentType());
        validateAvatarUpload(file.getOriginalFilename(), file.getContentType(), file.getSize());
        fileContentStorage.upload(user.getId(), AVATAR_PATH, normalizedStorageName, file);
    }

    @Transactional
    public User completeAvatarUpload(String username, UpdateUserAvatarRequest request) {
        User user = findUserByUsername(username);

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
        return userRepository.save(user);
    }

    public AvatarDownloadResult getAvatarContent(String username) {
        User user = findUserByUsername(username);

        if (!StringUtils.hasText(user.getAvatarStorageName())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "头像不存在");
        }

        String downloadName = buildAvatarDownloadName(user.getAvatarStorageName(), user.getAvatarContentType());
        if (fileContentStorage.supportsDirectDownload()) {
            return AvatarDownloadResult.redirect(fileContentStorage.createDownloadUrl(
                    user.getId(),
                    AVATAR_PATH,
                    user.getAvatarStorageName(),
                    downloadName
            ));
        }

        byte[] content = fileContentStorage.readFile(user.getId(), AVATAR_PATH, user.getAvatarStorageName());
        String contentType = StringUtils.hasText(user.getAvatarContentType())
                ? user.getAvatarContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return AvatarDownloadResult.inline(downloadName, contentType, content);
    }

    public String buildAvatarUrl(User user) {
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

        long version = user.getAvatarUpdatedAt() == null
                ? 0L
                : user.getAvatarUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "/user/avatar/content?v=" + version;
    }

    public String buildAvatarUrl(IdentityUserSnapshot user) {
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

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
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
                ? requestedStorageName
                : "avatar-" + UUID.randomUUID() + resolveAvatarExtension(filename, contentType);
        String cleaned = StringUtils.cleanPath(candidate).replace("\\", "/");
        int slashIndex = cleaned.lastIndexOf('/');
        if (slashIndex >= 0) {
            cleaned = cleaned.substring(slashIndex + 1);
        }
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "头像文件名不合法");
        }
        return cleaned;
    }

    private String resolveAvatarExtension(String filename, String contentType) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dotIndex = lowerName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < lowerName.length() - 1) {
            return lowerName.substring(dotIndex);
        }
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (lowerContentType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private String buildAvatarDownloadName(String storageName, String contentType) {
        if (StringUtils.hasText(storageName) && storageName.contains(".")) {
            return storageName;
        }
        return "avatar" + resolveAvatarExtension(storageName, contentType == null ? "image/png" : contentType);
    }
}
