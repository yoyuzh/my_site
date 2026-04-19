package com.yoyuzh.files.sharing.internal.application;

import com.yoyuzh.api.v2.ApiV2ErrorCode;
import com.yoyuzh.api.v2.ApiV2Exception;
import com.yoyuzh.api.v2.shares.ShareV2Response;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.SharingApi;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeSharingApi implements SharingApi {

    private final StoredFileRepository storedFileRepository;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;

    public RuntimeSharingApi(StoredFileRepository storedFileRepository,
                             FileShareLinkRepository fileShareLinkRepository,
                             FileService fileService,
                             PasswordEncoder passwordEncoder) {
        this.storedFileRepository = storedFileRepository;
        this.fileShareLinkRepository = fileShareLinkRepository;
        this.fileService = fileService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public ShareV2Response createShare(User user, CreateShareCommand command) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(command.fileId(), user.getId())
                .orElseThrow(() -> new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.isDirectory()) {
            throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "directories are not supported");
        }

        validateSharePolicy(command.expiresAt(), command.maxDownloads());

        FileShareLink shareLink = new FileShareLink();
        shareLink.setOwner(user);
        shareLink.setFile(file);
        shareLink.setToken(UUID.randomUUID().toString().replace("-", ""));
        shareLink.setShareName(StringUtils.hasText(command.shareName()) ? command.shareName().trim() : file.getFilename());
        shareLink.setPasswordHash(StringUtils.hasText(command.password()) ? passwordEncoder.encode(command.password()) : null);
        shareLink.setExpiresAt(command.expiresAt());
        shareLink.setMaxDownloads(command.maxDownloads());
        shareLink.setAllowImport(command.allowImport() == null ? true : command.allowImport());
        shareLink.setAllowDownload(command.allowDownload() == null ? true : command.allowDownload());
        FileShareLink saved = fileShareLinkRepository.save(shareLink);
        return toResponse(saved, true, true);
    }

    @Override
    @Transactional
    public ShareV2Response getShare(String token) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        shareLink.setViewCount(shareLink.getViewCountOrZero() + 1);
        boolean passwordRequired = shareLink.hasPassword();
        return toResponse(shareLink, !passwordRequired, !passwordRequired);
    }

    @Override
    @Transactional
    public ShareV2Response verifyPassword(String token, String password) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        if (shareLink.hasPassword()) {
            if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
                throw new ApiV2Exception(ApiV2ErrorCode.BAD_REQUEST, "invalid password");
            }
        }
        shareLink.setViewCount(shareLink.getViewCountOrZero() + 1);
        return toResponse(shareLink, true, true);
    }

    @Override
    @Transactional
    public FileMetadataResponse importSharedFile(User recipient, String token, ImportShareCommand command) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        ensureImportAllowed(shareLink);
        ensurePasswordAccepted(shareLink, command.password());

        FileMetadataResponse importedFile = fileService.importSharedFile(recipient, token, command.path());
        shareLink.setDownloadCount(shareLink.getDownloadCountOrZero() + 1);
        return importedFile;
    }

    @Override
    @Transactional
    public ResponseEntity<?> downloadSharedFile(String token, String password) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        ensureDownloadAllowed(shareLink);
        ensurePasswordAccepted(shareLink, password);

        shareLink.setDownloadCount(shareLink.getDownloadCountOrZero() + 1);
        return fileService.download(shareLink.getOwner(), shareLink.getFile().getId());
    }

    @Override
    @Transactional
    public Page<ShareV2Response> listOwnedShares(User user, Pageable pageable) {
        return fileShareLinkRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(shareLink -> toResponse(shareLink, true, true));
    }

    @Override
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
