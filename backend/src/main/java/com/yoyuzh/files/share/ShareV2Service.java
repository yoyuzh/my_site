package com.yoyuzh.files.share;

import com.yoyuzh.api.v2.ApiV2ErrorCode;
import com.yoyuzh.api.v2.ApiV2Exception;
import com.yoyuzh.api.v2.shares.CreateShareV2Request;
import com.yoyuzh.api.v2.shares.ImportShareV2Request;
import com.yoyuzh.api.v2.shares.ShareV2Response;
import com.yoyuzh.api.v2.shares.VerifySharePasswordV2Request;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareV2Service {

    private final StoredFileRepository storedFileRepository;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ShareV2Response createShare(User user, CreateShareV2Request request) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(request.fileId(), user.getId())
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.isDirectory()) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "directories are not supported");
        }

        validateSharePolicy(request.expiresAt(), request.maxDownloads());

        FileShareLink shareLink = new FileShareLink();
        shareLink.setOwner(user);
        shareLink.setFile(file);
        shareLink.setToken(UUID.randomUUID().toString().replace("-", ""));
        shareLink.setShareName(StringUtils.hasText(request.shareName()) ? request.shareName().trim() : file.getFilename());
        shareLink.setPasswordHash(StringUtils.hasText(request.password()) ? passwordEncoder.encode(request.password()) : null);
        shareLink.setExpiresAt(request.expiresAt());
        shareLink.setMaxDownloads(request.maxDownloads());
        shareLink.setAllowImport(request.allowImport() == null ? true : request.allowImport());
        shareLink.setAllowDownload(request.allowDownload() == null ? true : request.allowDownload());
        FileShareLink saved = fileShareLinkRepository.save(shareLink);
        return toResponse(saved, true, true);
    }

    @Transactional
    public ShareV2Response getShare(String token) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        shareLink.setViewCount(shareLink.getViewCountOrZero() + 1);
        boolean passwordRequired = shareLink.hasPassword();
        return toResponse(shareLink, !passwordRequired, !passwordRequired);
    }

    @Transactional
    public ShareV2Response verifyPassword(String token, VerifySharePasswordV2Request request) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        if (shareLink.hasPassword()) {
            if (!StringUtils.hasText(request.password()) || !passwordEncoder.matches(request.password(), shareLink.getPasswordHash())) {
                throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "invalid password");
            }
        }
        shareLink.setViewCount(shareLink.getViewCountOrZero() + 1);
        return toResponse(shareLink, true, true);
    }

    @Transactional
    public FileMetadataResponse importSharedFile(User recipient, String token, ImportShareV2Request request) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        ensureImportAllowed(shareLink);
        ensurePasswordAccepted(shareLink, request.password());

        FileMetadataResponse importedFile = fileService.importSharedFile(recipient, token, request.path());
        shareLink.setDownloadCount(shareLink.getDownloadCountOrZero() + 1);
        return importedFile;
    }

    @Transactional
    public ResponseEntity<?> downloadSharedFile(String token, String password) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        ensureDownloadAllowed(shareLink);
        ensurePasswordAccepted(shareLink, password);

        shareLink.setDownloadCount(shareLink.getDownloadCountOrZero() + 1);
        return fileService.download(shareLink.getOwner(), shareLink.getFile().getId());
    }

    @Transactional
    public Page<ShareV2Response> listOwnedShares(User user, Pageable pageable) {
        return fileShareLinkRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(shareLink -> toResponse(shareLink, true, true));
    }

    @Transactional
    public void deleteOwnedShare(User user, Long id) {
        FileShareLink shareLink = fileShareLinkRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "share not found"));
        fileShareLinkRepository.delete(shareLink);
    }

    private FileShareLink getShareLink(String token) {
        return fileShareLinkRepository.findByToken(token)
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "share not found"));
    }

    private void ensureShareNotExpired(FileShareLink shareLink) {
        if (shareLink.getExpiresAt() != null && !LocalDateTime.now().isBefore(shareLink.getExpiresAt())) {
            throw new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "share not found");
        }
    }

    private void ensureImportAllowed(FileShareLink shareLink) {
        if (!shareLink.isAllowImportEnabled()) {
            throw new ApiV2Exception(ApiV2ErrorCode.PERMISSION_DENIED, "import disabled");
        }

        ensureQuotaAvailable(shareLink);
    }

    private void ensureDownloadAllowed(FileShareLink shareLink) {
        if (!shareLink.isAllowDownloadEnabled()) {
            throw new ApiV2Exception(ApiV2ErrorCode.PERMISSION_DENIED, "download disabled");
        }

        ensureQuotaAvailable(shareLink);
    }

    private void ensureQuotaAvailable(FileShareLink shareLink) {
        Integer maxDownloads = shareLink.getMaxDownloads();
        if (maxDownloads != null && shareLink.getDownloadCountOrZero() >= maxDownloads) {
            throw new ApiV2Exception(ApiV2ErrorCode.PERMISSION_DENIED, "share quota exceeded");
        }
    }

    private void ensurePasswordAccepted(FileShareLink shareLink, String password) {
        if (!shareLink.hasPassword()) {
            return;
        }

        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "invalid password");
        }
    }

    private void validateSharePolicy(LocalDateTime expiresAt, Integer maxDownloads) {
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "expiresAt must be in the future");
        }
        if (maxDownloads != null && maxDownloads <= 0) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "maxDownloads must be greater than 0");
        }
    }

    private ShareV2Response toResponse(FileShareLink shareLink, boolean passwordVerified, boolean includeFile) {
        return new ShareV2Response(
                shareLink.getId(),
                shareLink.getToken(),
                shareLink.getShareNameOrDefault(),
                shareLink.getOwner() == null ? null : shareLink.getOwner().getUsername(),
                shareLink.hasPassword(),
                passwordVerified,
                shareLink.isAllowImportEnabled(),
                shareLink.isAllowDownloadEnabled(),
                shareLink.getMaxDownloads(),
                shareLink.getDownloadCountOrZero(),
                shareLink.getViewCountOrZero(),
                shareLink.getExpiresAt(),
                shareLink.getCreatedAt(),
                includeFile && shareLink.getFile() != null ? toFileMetadataResponse(shareLink.getFile()) : null
        );
    }

    private FileMetadataResponse toFileMetadataResponse(StoredFile file) {
        return new FileMetadataResponse(
                file.getId(),
                file.getFilename(),
                file.getPath(),
                file.getSize(),
                file.getContentType(),
                file.isDirectory(),
                file.getCreatedAt()
        );
    }
}
