package com.yoyuzh.admin;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.transfer.OfflineTransferSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminInspectionQueryService {

    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final RegistrationInviteService registrationInviteService;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final AdminMetricsService adminMetricsService;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final FileShareLinkRepository fileShareLinkRepository;

    public AdminSummaryResponse getSummary() {
        AdminMetricsSnapshot metrics = adminMetricsService.getSnapshot();
        return new AdminSummaryResponse(
                userRepository.count(),
                storedFileRepository.count(),
                fileBlobRepository.sumAllBlobSize(),
                metrics.downloadTrafficBytes(),
                metrics.requestCount(),
                metrics.transferUsageBytes(),
                offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(Instant.now()),
                metrics.offlineTransferStorageLimitBytes(),
                metrics.dailyActiveUsers(),
                metrics.requestTimeline(),
                registrationInviteService.getCurrentInviteCode()
        );
    }

    public PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery) {
        Page<StoredFile> result = storedFileRepository.searchAdminFiles(
                normalizeQuery(query),
                normalizeQuery(ownerQuery),
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "user.username")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
        );
        List<AdminFileResponse> items = result.getContent().stream()
                .map(this::toFileResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminFileBlobResponse> listFileBlobs(int page,
                                                             int size,
                                                             String userQuery,
                                                             Long storagePolicyId,
                                                             String objectKey,
                                                             FileEntityType entityType) {
        Page<FileEntity> result = fileEntityRepository.searchAdminEntities(
                normalizeQuery(userQuery),
                storagePolicyId,
                normalizeQuery(objectKey),
                entityType,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<FileEntity> entities = result.getContent();
        Map<String, FileBlob> blobsByObjectKey = loadBlobsByObjectKey(entities);
        Map<Long, StoredFileEntityRepository.FileEntityLinkStatsProjection> linkStatsByEntityId = loadLinkStatsByEntityId(entities);
        List<AdminFileBlobResponse> items = entities.stream()
                .map(entity -> toFileBlobResponse(
                        entity,
                        blobsByObjectKey.get(entity.getObjectKey()),
                        linkStatsByEntityId.get(entity.getId())
                ))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminShareResponse> listShares(int page,
                                                       int size,
                                                       String userQuery,
                                                       String fileName,
                                                       String token,
                                                       Boolean passwordProtected,
                                                       Boolean expired) {
        Page<FileShareLink> result = fileShareLinkRepository.searchAdminShares(
                normalizeQuery(userQuery),
                normalizeQuery(fileName),
                normalizeQuery(token),
                passwordProtected,
                expired,
                LocalDateTime.now(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AdminShareResponse> items = result.getContent().stream()
                .map(this::toAdminShareResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    private AdminFileResponse toFileResponse(StoredFile storedFile) {
        User owner = storedFile.getUser();
        return new AdminFileResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                owner.getId(),
                owner.getUsername(),
                owner.getEmail()
        );
    }

    private AdminFileBlobResponse toFileBlobResponse(FileEntity entity,
                                                     FileBlob blob,
                                                     StoredFileEntityRepository.FileEntityLinkStatsProjection linkStats) {
        long linkedStoredFileCount = linkStats == null || linkStats.getLinkedStoredFileCount() == null
                ? 0L
                : linkStats.getLinkedStoredFileCount();
        long linkedOwnerCount = linkStats == null || linkStats.getLinkedOwnerCount() == null
                ? 0L
                : linkStats.getLinkedOwnerCount();
        String sampleOwnerUsername = linkStats == null ? null : linkStats.getSampleOwnerUsername();
        String sampleOwnerEmail = linkStats == null ? null : linkStats.getSampleOwnerEmail();
        return new AdminFileBlobResponse(
                entity.getId(),
                blob == null ? null : blob.getId(),
                entity.getObjectKey(),
                entity.getEntityType(),
                entity.getStoragePolicyId(),
                entity.getSize(),
                StringUtils.hasText(entity.getContentType()) ? entity.getContentType() : blob == null ? null : blob.getContentType(),
                entity.getReferenceCount(),
                linkedStoredFileCount,
                linkedOwnerCount,
                sampleOwnerUsername,
                sampleOwnerEmail,
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getId(),
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getUsername(),
                entity.getCreatedAt(),
                blob == null ? null : blob.getCreatedAt(),
                blob == null,
                linkedStoredFileCount == 0,
                entity.getReferenceCount() == null || entity.getReferenceCount() != linkedStoredFileCount
        );
    }

    private Map<String, FileBlob> loadBlobsByObjectKey(List<FileEntity> entities) {
        Set<String> objectKeys = entities.stream()
                .map(FileEntity::getObjectKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (objectKeys.isEmpty()) {
            return Map.of();
        }
        return fileBlobRepository.findAllByObjectKeyIn(objectKeys).stream()
                .collect(Collectors.toMap(
                        FileBlob::getObjectKey,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<Long, StoredFileEntityRepository.FileEntityLinkStatsProjection> loadLinkStatsByEntityId(List<FileEntity> entities) {
        Set<Long> entityIds = entities.stream()
                .map(FileEntity::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (entityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return storedFileEntityRepository.findAdminLinkStatsByFileEntityIds(entityIds).stream()
                .collect(Collectors.toMap(
                        StoredFileEntityRepository.FileEntityLinkStatsProjection::getFileEntityId,
                        Function.identity()
                ));
    }

    private AdminShareResponse toAdminShareResponse(FileShareLink shareLink) {
        StoredFile file = shareLink.getFile();
        User owner = shareLink.getOwner();
        boolean expired = shareLink.getExpiresAt() != null && shareLink.getExpiresAt().isBefore(LocalDateTime.now());
        return new AdminShareResponse(
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
                owner.getId(),
                owner.getUsername(),
                owner.getEmail(),
                file.getId(),
                file.getFilename(),
                file.getPath(),
                file.getContentType(),
                file.getSize(),
                file.isDirectory()
        );
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }
}
