package com.yoyuzh.files.sharing.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.SharingAdminShareQuery;
import com.yoyuzh.files.sharing.api.SharingAdminShareSnapshot;
import com.yoyuzh.files.sharing.api.SharingAdminShareView;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.sharing.api.ShareV2Response;
import com.yoyuzh.files.sharing.internal.domain.FileShareLink;
import com.yoyuzh.files.sharing.internal.infra.FileShareLinkRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeSharingApi implements SharingApi {

    private final StoredFileRepository storedFileRepository;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final UploadTargetPolicy uploadTargetPolicy;
    private final ContentDuplicationApi contentDuplicationApi;
    private final FileContentStorage fileContentStorage;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public RuntimeSharingApi(StoredFileRepository storedFileRepository,
                             FileShareLinkRepository fileShareLinkRepository,
                             WorkspacePathPolicy workspacePathPolicy,
                             UploadTargetPolicy uploadTargetPolicy,
                             ContentDuplicationApi contentDuplicationApi,
                             FileContentStorage fileContentStorage,
                             PasswordEncoder passwordEncoder,
                             EntityManager entityManager) {
        this.storedFileRepository = storedFileRepository;
        this.fileShareLinkRepository = fileShareLinkRepository;
        this.workspacePathPolicy = workspacePathPolicy;
        this.uploadTargetPolicy = uploadTargetPolicy;
        this.contentDuplicationApi = contentDuplicationApi;
        this.fileContentStorage = fileContentStorage;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ShareV2Response createShare(Long ownerUserId, CreateShareCommand command) {
        StoredFile file = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(command.fileId(), ownerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "directories are not supported");
        }

        validateSharePolicy(command.expiresAt(), command.maxDownloads());

        User owner = userReference(ownerUserId);
        FileShareLink shareLink = new FileShareLink();
        shareLink.setOwner(owner);
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
                throw new BusinessException(ErrorCode.UNKNOWN, "invalid password");
            }
        }
        shareLink.setViewCount(shareLink.getViewCountOrZero() + 1);
        return toResponse(shareLink, true, true);
    }

    @Override
    @Transactional
    public FileMetadataResponse importSharedFile(Long recipientUserId, String token, ImportShareCommand command) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        ensureImportAllowed(shareLink);
        ensurePasswordAccepted(shareLink, command.password());
        StoredFile sourceFile = requireShareFile(shareLink);
        User recipient = requireUser(recipientUserId);
        ValidatedUploadTarget target = uploadTargetPolicy.validateUpload(
                recipientUserId,
                recipient.getMaxUploadSizeBytes(),
                recipient.getStorageQuotaBytes(),
                command.path(),
                sourceFile.getFilename(),
                sourceFile.getSize()
        );
        workspacePathPolicy.ensureDirectoryHierarchy(recipientUserId, target.normalizedPath());
        RegisteredContentFile importedFile = contentDuplicationApi.duplicateBlobBackedFile(
                        new ContentRegistrationCommand(
                                recipientUserId,
                                target.normalizedPath(),
                                target.filename(),
                                sourceFile.getContentType(),
                                sourceFile.getSize(),
                                new ContentBlobReference(
                                        requireShareBlob(sourceFile).getId(),
                                        requireShareBlob(sourceFile).getObjectKey(),
                                        requireShareBlob(sourceFile).getContentType(),
                                        requireShareBlob(sourceFile).getSize()
                                )
                        )
        );
        shareLink.setDownloadCount(shareLink.getDownloadCountOrZero() + 1);
        return toFileMetadataResponse(importedFile);
    }

    @Override
    @Transactional
    public ResponseEntity<?> downloadSharedFile(String token, String password) {
        FileShareLink shareLink = getShareLink(token);
        ensureShareNotExpired(shareLink);
        ensureDownloadAllowed(shareLink);
        ensurePasswordAccepted(shareLink, password);
        StoredFile sourceFile = requireShareFile(shareLink);

        shareLink.setDownloadCount(shareLink.getDownloadCountOrZero() + 1);
        FileBlob blob = requireShareBlob(sourceFile);
        if (fileContentStorage.supportsDirectDownload()) {
            return ResponseEntity.status(302)
                    .location(URI.create(fileContentStorage.createBlobDownloadUrl(blob.getObjectKey(), sourceFile.getFilename())))
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(sourceFile.getFilename(), StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        sourceFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : sourceFile.getContentType()))
                .body(fileContentStorage.readBlob(blob.getObjectKey()));
    }

    @Override
    @Transactional
    public Page<ShareV2Response> listOwnedShares(Long ownerUserId, Pageable pageable) {
        return fileShareLinkRepository.findByOwnerIdOrderByCreatedAtDesc(ownerUserId, pageable)
                .map(shareLink -> toResponse(shareLink, true, true));
    }

    @Override
    @Transactional
    public void deleteOwnedShare(Long ownerUserId, Long id) {
        FileShareLink shareLink = fileShareLinkRepository.findByIdAndOwnerId(id, ownerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found"));
        fileShareLinkRepository.delete(shareLink);
    }

    @Override
    @Transactional
    public Optional<SharingAdminShareSnapshot> deleteShareAsAdmin(Long id) {
        Optional<FileShareLink> shareLink = fileShareLinkRepository.findById(id);
        if (shareLink.isEmpty()) {
            return Optional.empty();
        }
        FileShareLink target = shareLink.get();
        SharingAdminShareSnapshot snapshot = new SharingAdminShareSnapshot(target.getId(), target.getToken());
        fileShareLinkRepository.delete(target);
        return Optional.of(snapshot);
    }

    @Override
    @Transactional
    public PageResponse<SharingAdminShareView> listSharesAsAdmin(SharingAdminShareQuery query) {
        int page = query.page();
        int size = query.size();
        Page<FileShareLink> result = fileShareLinkRepository.searchAdminShares(
                normalizeQuery(query.userQuery()),
                normalizeQuery(query.fileName()),
                normalizeQuery(query.token()),
                query.passwordProtected(),
                query.expired(),
                LocalDateTime.now(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toAdminShareView).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    private FileShareLink getShareLink(String token) {
        return fileShareLinkRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found"));
    }

    private void ensureShareNotExpired(FileShareLink shareLink) {
        if (shareLink.getExpiresAt() != null && !LocalDateTime.now().isBefore(shareLink.getExpiresAt())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found");
        }
    }

    private void ensureImportAllowed(FileShareLink shareLink) {
        if (!shareLink.isAllowImportEnabled()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "import disabled");
        }
        ensureQuotaAvailable(shareLink);
    }

    private void ensureDownloadAllowed(FileShareLink shareLink) {
        if (!shareLink.isAllowDownloadEnabled()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "download disabled");
        }
        ensureQuotaAvailable(shareLink);
    }

    private void ensureQuotaAvailable(FileShareLink shareLink) {
        Integer maxDownloads = shareLink.getMaxDownloads();
        if (maxDownloads != null && shareLink.getDownloadCountOrZero() >= maxDownloads) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "share quota exceeded");
        }
    }

    private void ensurePasswordAccepted(FileShareLink shareLink, String password) {
        if (!shareLink.hasPassword()) {
            return;
        }
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid password");
        }
    }

    private void validateSharePolicy(LocalDateTime expiresAt, Integer maxDownloads) {
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "expiresAt must be in the future");
        }
        if (maxDownloads != null && maxDownloads <= 0) {
            throw new BusinessException(ErrorCode.UNKNOWN, "maxDownloads must be greater than 0");
        }
    }

    private User userReference(Long userId) {
        return entityManager.getReference(User.class, userId);
    }

    private User requireUser(Long userId) {
        User user = entityManager.find(User.class, userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "user not found");
        }
        return user;
    }

    private StoredFile requireShareFile(FileShareLink shareLink) {
        StoredFile sourceFile = shareLink.getFile();
        if (sourceFile == null || sourceFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "directories are not supported");
        }
        return sourceFile;
    }

    private FileBlob requireShareBlob(StoredFile storedFile) {
        if (storedFile.getBlob() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file blob missing");
        }
        return storedFile.getBlob();
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

    private SharingAdminShareView toAdminShareView(FileShareLink shareLink) {
        StoredFile file = shareLink.getFile();
        User owner = shareLink.getOwner();
        boolean expired = shareLink.getExpiresAt() != null && shareLink.getExpiresAt().isBefore(LocalDateTime.now());
        return new SharingAdminShareView(
                shareLink.getId(),
                shareLink.getToken(),
                shareLink.getShareNameOrDefault(),
                shareLink.hasPassword(),
                expired,
                shareLink.getCreatedAt(),
                shareLink.getExpiresAt(),
                shareLink.getMaxDownloads(),
                shareLink.getDownloadCountOrZero(),
                shareLink.getViewCountOrZero(),
                shareLink.isAllowImportEnabled(),
                shareLink.isAllowDownloadEnabled(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getUsername(),
                owner == null ? null : owner.getEmail(),
                file == null ? null : file.getId(),
                file == null ? null : file.getFilename(),
                file == null ? null : file.getPath(),
                file == null ? null : file.getContentType(),
                file == null ? null : file.getSize(),
                file != null && file.isDirectory()
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

    private String normalizeQuery(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private FileMetadataResponse toFileMetadataResponse(RegisteredContentFile storedFile) {
        return new FileMetadataResponse(
                storedFile.id(),
                storedFile.filename(),
                storedFile.path(),
                storedFile.size(),
                storedFile.contentType(),
                storedFile.directory(),
                storedFile.createdAt()
        );
    }
}
