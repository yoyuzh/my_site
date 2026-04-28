package com.yoyuzh.files.sharing.internal.application;

import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.SavedShareV2Response;
import com.yoyuzh.files.sharing.api.ShareDownloadResult;
import com.yoyuzh.files.sharing.api.ShareStatus;
import com.yoyuzh.files.sharing.api.ShareStatsResponse;
import com.yoyuzh.files.sharing.api.SharingAdminShareQuery;
import com.yoyuzh.files.sharing.api.SharingAdminShareSnapshot;
import com.yoyuzh.files.sharing.api.SharingAdminShareView;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.sharing.api.ShareV2Response;
import com.yoyuzh.files.sharing.api.UpdateSharePolicyCommand;
import com.yoyuzh.files.sharing.internal.domain.FileShareLink;
import com.yoyuzh.files.sharing.internal.domain.SavedShareShortcut;
import com.yoyuzh.files.sharing.internal.infra.FileShareLinkRepository;
import com.yoyuzh.files.sharing.internal.infra.SavedShareShortcutRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.upload.api.UploadTargetPolicy;
import com.yoyuzh.files.upload.api.ValidatedUploadTarget;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeSharingApi implements SharingApi {

    private final WorkspaceFileQueryApi workspaceFileQueryApi;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final SavedShareShortcutRepository savedShareShortcutRepository;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final UploadTargetPolicy uploadTargetPolicy;
    private final ContentDuplicationApi contentDuplicationApi;
    private final ContentBlobQueryApi contentBlobQueryApi;
    private final FileContentStorage fileContentStorage;
    private final PasswordEncoder passwordEncoder;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;
    private final Clock clock;

    @Autowired
    public RuntimeSharingApi(WorkspaceFileQueryApi workspaceFileQueryApi,
                             FileShareLinkRepository fileShareLinkRepository,
                             SavedShareShortcutRepository savedShareShortcutRepository,
                             WorkspacePathPolicy workspacePathPolicy,
                             UploadTargetPolicy uploadTargetPolicy,
                             ContentDuplicationApi contentDuplicationApi,
                             ContentBlobQueryApi contentBlobQueryApi,
                             FileContentStorage fileContentStorage,
                             PasswordEncoder passwordEncoder,
                             IdentityUserDirectoryApi identityUserDirectoryApi) {
        this(
                workspaceFileQueryApi,
                fileShareLinkRepository,
                savedShareShortcutRepository,
                workspacePathPolicy,
                uploadTargetPolicy,
                contentDuplicationApi,
                contentBlobQueryApi,
                fileContentStorage,
                passwordEncoder,
                identityUserDirectoryApi,
                Clock.systemDefaultZone()
        );
    }

    RuntimeSharingApi(WorkspaceFileQueryApi workspaceFileQueryApi,
                      FileShareLinkRepository fileShareLinkRepository,
                      SavedShareShortcutRepository savedShareShortcutRepository,
                      WorkspacePathPolicy workspacePathPolicy,
                      UploadTargetPolicy uploadTargetPolicy,
                      ContentDuplicationApi contentDuplicationApi,
                      ContentBlobQueryApi contentBlobQueryApi,
                      FileContentStorage fileContentStorage,
                      PasswordEncoder passwordEncoder,
                      IdentityUserDirectoryApi identityUserDirectoryApi,
                      Clock clock) {
        this.workspaceFileQueryApi = workspaceFileQueryApi;
        this.fileShareLinkRepository = fileShareLinkRepository;
        this.savedShareShortcutRepository = savedShareShortcutRepository;
        this.workspacePathPolicy = workspacePathPolicy;
        this.uploadTargetPolicy = uploadTargetPolicy;
        this.contentDuplicationApi = contentDuplicationApi;
        this.contentBlobQueryApi = contentBlobQueryApi;
        this.fileContentStorage = fileContentStorage;
        this.passwordEncoder = passwordEncoder;
        this.identityUserDirectoryApi = identityUserDirectoryApi;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ShareV2Response createShare(Long ownerUserId, CreateShareCommand command) {
        WorkspaceFileSnapshot file = workspaceFileQueryApi.findOwnedActiveFile(ownerUserId, command.fileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.directory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "directories are not supported");
        }

        validateSharePolicy(command.expiresAt(), command.maxDownloads());
        String normalizedPassword = normalizeSharePassword(command.password());

        FileShareLink shareLink = new FileShareLink();
        shareLink.setOwnerId(ownerUserId);
        shareLink.setFileId(file.id());
        shareLink.setToken(UUID.randomUUID().toString().replace("-", ""));
        shareLink.setShareName(StringUtils.hasText(command.shareName()) ? command.shareName().trim() : file.filename());
        shareLink.setPasswordHash(normalizedPassword == null ? null : passwordEncoder.encode(normalizedPassword));
        shareLink.setExpiresAt(command.expiresAt());
        shareLink.setMaxDownloads(command.maxDownloads());
        shareLink.setAllowImport(command.allowImport() == null ? true : command.allowImport());
        shareLink.setAllowDownload(command.allowDownload() == null ? true : command.allowDownload());
        shareLink.setExpireAfterConsume(command.expireAfterConsume() == null ? false : command.expireAfterConsume());
        FileShareLink saved = fileShareLinkRepository.save(shareLink);
        return toResponse(saved, identityUserDirectoryApi.findProfileById(ownerUserId).orElse(null), true, true, file, normalizedPassword);
    }

    @Override
    @Transactional
    public ShareV2Response getShare(String token) {
        FileShareLink shareLink = getActiveShareLink(token);
        ensureShareAvailable(shareLink);
        shareLink.recordVisit();
        boolean passwordRequired = shareLink.hasPassword();
        WorkspaceFileSnapshot file = passwordRequired ? null : findShareFile(shareLink).orElse(null);
        return toResponse(shareLink, ownerProfile(shareLink), !passwordRequired, !passwordRequired, file, null);
    }

    @Override
    @Transactional
    public ShareStatsResponse getStats(Long ownerUserId, String token) {
        FileShareLink shareLink = getOwnedShareByToken(ownerUserId, token);
        return new ShareStatsResponse(
                shareLink.getToken(),
                shareLink.getViewCountOrZero(),
                shareLink.getDownloadCountOrZero(),
                shareLink.getMaxDownloads(),
                shareLink.isDownloadLimitReached()
        );
    }

    @Override
    @Transactional
    public ShareV2Response verifyPassword(String token, String password) {
        FileShareLink shareLink = getActiveShareLink(token);
        ensureShareAvailable(shareLink);
        if (shareLink.hasPassword()) {
            if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid password");
            }
        }
        shareLink.recordVisit();
        return toResponse(shareLink, ownerProfile(shareLink), true, true, requireShareFile(shareLink), null);
    }

    @Override
    @Transactional
    public FileMetadataResponse importSharedFile(Long recipientUserId, String token, ImportShareCommand command) {
        FileShareLink shareLink = getActiveShareLink(token);
        ensureShareAvailable(shareLink);
        ensureImportAllowed(shareLink);
        ensurePasswordAccepted(shareLink, command.password());
        WorkspaceFileSnapshot sourceFile = requireShareFile(shareLink);
        IdentityUserSnapshot recipient = requireUser(recipientUserId);
        ValidatedUploadTarget target = uploadTargetPolicy.validateUpload(
                recipientUserId,
                recipient.maxUploadSizeBytes(),
                recipient.storageQuotaBytes(),
                command.path(),
                sourceFile.filename(),
                sourceFile.size()
        );
        workspacePathPolicy.ensureDirectoryHierarchy(recipientUserId, target.normalizedPath());
        RegisteredContentFile importedFile = contentDuplicationApi.duplicateBlobBackedFile(
                        new ContentRegistrationCommand(
                                recipientUserId,
                                target.normalizedPath(),
                                target.filename(),
                                sourceFile.contentType(),
                                sourceFile.size(),
                                requireShareBlob(sourceFile)
                        )
        );
        shareLink.recordDownload();
        consumeIfNeeded(shareLink);
        return toFileMetadataResponse(importedFile);
    }

    @Override
    @Transactional
    public SavedShareV2Response saveSharedWithMe(Long recipientUserId, String token, String password) {
        FileShareLink shareLink = getActiveShareLink(token);
        ensureShareAvailable(shareLink);
        ensurePasswordAccepted(shareLink, password);
        SavedShareShortcut existing = savedShareShortcutRepository
                .findByRecipientUserIdAndShareId(recipientUserId, shareLink.getId())
                .orElse(null);
        if (existing != null) {
            return toSavedResponse(existing, shareLink, ownerProfile(shareLink), requireShareFile(shareLink));
        }

        SavedShareShortcut shortcut = new SavedShareShortcut();
        shortcut.setRecipientUserId(recipientUserId);
        shortcut.setShareId(shareLink.getId());
        shortcut.setShareToken(shareLink.getToken());
        SavedShareShortcut saved = savedShareShortcutRepository.save(shortcut);
        return toSavedResponse(saved, shareLink, ownerProfile(shareLink), requireShareFile(shareLink));
    }

    @Override
    @Transactional
    public Page<SavedShareV2Response> listSharedWithMe(Long recipientUserId, Pageable pageable) {
        Page<SavedShareShortcut> page = savedShareShortcutRepository.findByRecipientUserIdOrderBySavedAtDesc(recipientUserId, pageable);
        Map<Long, FileShareLink> shares = loadShares(page.getContent());
        Map<Long, IdentityUserProfileSummary> ownerProfiles = loadOwnerProfiles(shares.values());
        Map<Long, WorkspaceFileSnapshot> files = loadFiles(shares.values());
        return new PageImpl<>(
                page.getContent().stream()
                        .map(shortcut -> {
                            FileShareLink share = shares.get(shortcut.getShareId());
                            WorkspaceFileSnapshot file = share == null ? null : files.get(share.getFileId());
                            IdentityUserProfileSummary owner = share == null ? null : ownerProfiles.get(share.getOwnerId());
                            return toSavedResponse(shortcut, share, owner, file);
                        })
                        .toList(),
                pageable,
                page.getTotalElements()
        );
    }

    @Override
    @Transactional
    public SavedShareV2Response getSharedWithMe(Long recipientUserId, Long savedShareId) {
        SavedShareShortcut shortcut = getSavedShareShortcut(recipientUserId, savedShareId);
        FileShareLink share = fileShareLinkRepository.findById(shortcut.getShareId()).orElse(null);
        WorkspaceFileSnapshot file = share == null ? null : findShareFile(share).orElse(null);
        IdentityUserProfileSummary owner = share == null ? null : ownerProfile(share);
        return toSavedResponse(shortcut, share, owner, file);
    }

    @Override
    @Transactional
    public void deleteSharedWithMe(Long recipientUserId, Long savedShareId) {
        savedShareShortcutRepository.delete(getSavedShareShortcut(recipientUserId, savedShareId));
    }

    @Override
    @Transactional
    public ShareDownloadResult downloadSharedFile(String token, String password) {
        FileShareLink shareLink = getActiveShareLink(token);
        ensureShareAvailable(shareLink);
        ensureDownloadAllowed(shareLink);
        ensurePasswordAccepted(shareLink, password);
        WorkspaceFileSnapshot sourceFile = requireShareFile(shareLink);

        shareLink.recordDownload();
        consumeIfNeeded(shareLink);
        ContentBlobReference blob = requireShareBlob(sourceFile);
        if (fileContentStorage.supportsDirectDownload()) {
            return ShareDownloadResult.redirect(fileContentStorage.createBlobDownloadUrl(blob.objectKey(), sourceFile.filename()));
        }

        return ShareDownloadResult.inline(
                sourceFile.filename(),
                sourceFile.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : sourceFile.contentType(),
                fileContentStorage.readBlob(blob.objectKey())
        );
    }

    @Override
    @Transactional
    public Page<ShareV2Response> listOwnedShares(Long ownerUserId, Pageable pageable) {
        IdentityUserProfileSummary ownerProfile = identityUserDirectoryApi.findProfileById(ownerUserId).orElse(null);
        Page<FileShareLink> page = fileShareLinkRepository.findByOwnerIdAndCancelledAtIsNullOrderByCreatedAtDesc(ownerUserId, pageable);
        Map<Long, WorkspaceFileSnapshot> files = loadFiles(page.getContent());
        return new PageImpl<>(
                page.getContent().stream()
                        .map(shareLink -> toResponse(shareLink, ownerProfile, true, true, files.get(shareLink.getFileId()), null))
                        .toList(),
                pageable,
                page.getTotalElements()
        );
    }

    @Override
    @Transactional
    public ShareV2Response updatePolicy(Long ownerUserId, Long id, UpdateSharePolicyCommand command) {
        validateSharePolicy(command.expiresAt(), command.maxDownloads());
        FileShareLink shareLink = getOwnedShare(ownerUserId, id);
        String normalizedPassword = command.password() == null ? null : normalizeSharePassword(command.password());
        shareLink.setMaxDownloads(command.maxDownloads());
        if (command.expiresAt() != null) {
            shareLink.setExpiresAt(command.expiresAt());
        }
        if (command.expireAfterConsume() != null) {
            shareLink.setExpireAfterConsume(command.expireAfterConsume());
        }
        if (command.password() != null) {
            if (normalizedPassword == null) {
                shareLink.setPasswordHash(null);
            } else {
                shareLink.setPasswordHash(passwordEncoder.encode(normalizedPassword));
            }
        }
        return toResponse(shareLink, ownerProfile(shareLink), true, true, findShareFile(shareLink).orElse(null), normalizedPassword);
    }

    @Override
    @Transactional
    public void deleteOwnedShare(Long ownerUserId, Long id) {
        FileShareLink shareLink = getOwnedShare(ownerUserId, id);
        shareLink.cancel(now());
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
        target.cancel(now());
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
                now(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<Long, IdentityUserProfileSummary> ownerProfiles = loadOwnerProfiles(result.getContent());
        Map<Long, WorkspaceFileSnapshot> files = loadFiles(result.getContent());
        return new PageResponse<>(
                result.getContent().stream()
                        .map(share -> toAdminShareView(share, ownerProfiles.get(share.getOwnerId()), files.get(share.getFileId())))
                        .toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    private FileShareLink getActiveShareLink(String token) {
        return fileShareLinkRepository.findByTokenAndCancelledAtIsNull(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found"));
    }

    private FileShareLink getOwnedShareByToken(Long ownerUserId, String token) {
        FileShareLink shareLink = getActiveShareLink(token);
        if (!ownerUserId.equals(shareLink.getOwnerId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found");
        }
        return shareLink;
    }

    private FileShareLink getOwnedShare(Long ownerUserId, Long id) {
        return fileShareLinkRepository.findByIdAndOwnerIdAndCancelledAtIsNull(id, ownerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found"));
    }

    private SavedShareShortcut getSavedShareShortcut(Long recipientUserId, Long savedShareId) {
        return savedShareShortcutRepository.findByIdAndRecipientUserId(savedShareId, recipientUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "saved share not found"));
    }

    private void ensureShareAvailable(FileShareLink shareLink) {
        if (shareLink.isCancelled()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found");
        }
        if (shareLink.isConsumed()) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED, "share consumed");
        }
        if (shareLink.getExpiresAt() != null && !now().isBefore(shareLink.getExpiresAt())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED, "share expired");
        }
    }

    private void consumeIfNeeded(FileShareLink shareLink) {
        if (shareLink.isExpireAfterConsumeEnabled()) {
            shareLink.markConsumed(now());
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
        if (shareLink.isDownloadLimitReached()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "share quota exceeded");
        }
    }

    private void ensurePasswordAccepted(FileShareLink shareLink, String password) {
        if (!shareLink.hasPassword()) {
            return;
        }
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid password");
        }
    }

    private void validateSharePolicy(LocalDateTime expiresAt, Integer maxDownloads) {
        if (expiresAt != null && !expiresAt.isAfter(now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "expiresAt must be in the future");
        }
        if (maxDownloads != null && maxDownloads <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "maxDownloads must be greater than 0");
        }
    }

    private IdentityUserSnapshot requireUser(Long userId) {
        IdentityUserSnapshot user = identityUserDirectoryApi.findSnapshotById(userId).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "user not found");
        }
        return user;
    }

    private Optional<WorkspaceFileSnapshot> findShareFile(FileShareLink shareLink) {
        if (shareLink.getFileId() == null) {
            return Optional.empty();
        }
        return workspaceFileQueryApi.findActiveFile(shareLink.getFileId());
    }

    private WorkspaceFileSnapshot requireShareFile(FileShareLink shareLink) {
        WorkspaceFileSnapshot sourceFile = findShareFile(shareLink).orElse(null);
        if (sourceFile == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found");
        }
        if (sourceFile.directory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "directories are not supported");
        }
        return sourceFile;
    }

    private ContentBlobReference requireShareBlob(WorkspaceFileSnapshot storedFile) {
        if (storedFile.blobId() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file blob missing");
        }
        return contentBlobQueryApi.findBlobReferenceById(storedFile.blobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file blob missing"));
    }

    private ShareV2Response toResponse(FileShareLink shareLink,
                                       IdentityUserProfileSummary ownerProfile,
                                       boolean passwordVerified,
                                       boolean includeFile,
                                       WorkspaceFileSnapshot file,
                                       String ownerPassword) {
        ShareStatus status = resolveStatus(shareLink);
        return new ShareV2Response(
                shareLink.getId(),
                shareLink.getToken(),
                shareLink.getShareNameOrDefault(),
                ownerProfile == null ? null : ownerProfile.username(),
                ownerPassword,
                shareLink.hasPassword(),
                passwordVerified,
                shareLink.isAllowImportEnabled(),
                shareLink.isAllowDownloadEnabled(),
                shareLink.isExpireAfterConsumeEnabled(),
                shareLink.getMaxDownloads(),
                shareLink.getDownloadCountOrZero(),
                shareLink.getViewCountOrZero(),
                status,
                shareLink.getExpiresAt(),
                shareLink.getCreatedAt(),
                includeFile && file != null ? toFileMetadataResponse(file) : null
        );
    }

    private SavedShareV2Response toSavedResponse(SavedShareShortcut shortcut,
                                                 FileShareLink shareLink,
                                                 IdentityUserProfileSummary ownerProfile,
                                                 WorkspaceFileSnapshot file) {
        ShareV2Response share = shareLink == null
                ? new ShareV2Response(
                        null,
                        shortcut.getShareToken(),
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        0,
                        0,
                        ShareStatus.REMOVED,
                        null,
                        null,
                        null
                )
                : toResponse(shareLink, ownerProfile, false, true, file, null);
        return new SavedShareV2Response(shortcut.getId(), shortcut.getSavedAt(), share);
    }

    private String normalizeSharePassword(String password) {
        if (!StringUtils.hasText(password)) {
            return null;
        }
        return password.trim();
    }

    private SharingAdminShareView toAdminShareView(FileShareLink shareLink,
                                                   IdentityUserProfileSummary ownerProfile,
                                                   WorkspaceFileSnapshot file) {
        boolean expired = resolveStatus(shareLink) == ShareStatus.EXPIRED;
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
                shareLink.getOwnerId(),
                ownerProfile == null ? null : ownerProfile.username(),
                ownerProfile == null ? null : ownerProfile.email(),
                file == null ? null : file.id(),
                file == null ? null : file.filename(),
                file == null ? null : file.path(),
                file == null ? null : file.contentType(),
                file == null ? null : file.size(),
                file != null && file.directory()
        );
    }

    private FileMetadataResponse toFileMetadataResponse(WorkspaceFileSnapshot file) {
        return new FileMetadataResponse(
                file.id(),
                file.filename(),
                file.path(),
                file.size(),
                file.contentType(),
                file.directory(),
                file.createdAt(),
                file.createdAt(),
                null,
                null,
                false
        );
    }

    private String normalizeQuery(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private Map<Long, IdentityUserProfileSummary> loadOwnerProfiles(java.util.Collection<FileShareLink> shareLinks) {
        Set<Long> ownerIds = shareLinks.stream()
                .map(FileShareLink::getOwnerId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        return identityUserDirectoryApi.findProfilesByIds(ownerIds);
    }

    private Map<Long, WorkspaceFileSnapshot> loadFiles(java.util.Collection<FileShareLink> shareLinks) {
        Set<Long> fileIds = shareLinks.stream()
                .map(FileShareLink::getFileId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        return workspaceFileQueryApi.findActiveFilesByIds(fileIds);
    }

    private Map<Long, FileShareLink> loadShares(java.util.Collection<SavedShareShortcut> shortcuts) {
        Set<Long> shareIds = shortcuts.stream()
                .map(SavedShareShortcut::getShareId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        return fileShareLinkRepository.findAllById(shareIds).stream()
                .collect(java.util.stream.Collectors.toMap(FileShareLink::getId, share -> share));
    }

    private IdentityUserProfileSummary ownerProfile(FileShareLink shareLink) {
        if (shareLink.getOwnerId() == null) {
            return null;
        }
        return identityUserDirectoryApi.findProfileById(shareLink.getOwnerId()).orElse(null);
    }

    private FileMetadataResponse toFileMetadataResponse(RegisteredContentFile storedFile) {
        return new FileMetadataResponse(
                storedFile.id(),
                storedFile.filename(),
                storedFile.path(),
                storedFile.size(),
                storedFile.contentType(),
                storedFile.directory(),
                storedFile.createdAt(),
                storedFile.createdAt(),
                null,
                null,
                false
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private ShareStatus resolveStatus(FileShareLink shareLink) {
        if (shareLink == null || shareLink.isCancelled()) {
            return ShareStatus.REMOVED;
        }
        if (shareLink.isConsumed()) {
            return ShareStatus.CONSUMED;
        }
        if (shareLink.getExpiresAt() != null && !now().isBefore(shareLink.getExpiresAt())) {
            return ShareStatus.EXPIRED;
        }
        return ShareStatus.ACTIVE;
    }
}
