package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.files.content.api.ContentAdminFileBlobQuery;
import com.yoyuzh.files.content.api.ContentAdminFileBlobView;
import com.yoyuzh.files.content.api.ContentAdminInspectionApi;
import com.yoyuzh.files.content.api.ContentEntityType;
import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.ops.admin.api.AdminFileEntityType;
import com.yoyuzh.ops.admin.api.AdminFileBlobResponse;
import com.yoyuzh.ops.admin.api.AdminFileResponse;
import com.yoyuzh.ops.admin.api.AdminShareResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.sharing.api.SharingAdminShareQuery;
import com.yoyuzh.files.sharing.api.SharingAdminShareView;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileQuery;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileView;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.transfer.api.TransferAdminMetricsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminInspectionQueryService {

    private final IdentityAdminSummaryApi identityAdminSummaryApi;
    private final TransferAdminMetricsApi transferAdminMetricsApi;
    private final AdminMetricsService adminMetricsService;
    private final ContentAdminInspectionApi contentAdminInspectionApi;
    private final SharingApi sharingApi;
    private final WorkspaceAdminGovernanceApi workspaceAdminGovernanceApi;

    public AdminSummaryResponse getSummary() {
        AdminMetricsSnapshot metrics = adminMetricsService.getSnapshot();
        return new AdminSummaryResponse(
                identityAdminSummaryApi.countUsersAsAdmin(),
                workspaceAdminGovernanceApi.countFilesAsAdmin(),
                contentAdminInspectionApi.totalBlobSize(),
                metrics.downloadTrafficBytes(),
                metrics.requestCount(),
                metrics.transferUsageBytes(),
                transferAdminMetricsApi.currentOfflineStorageBytes(),
                metrics.offlineTransferStorageLimitBytes(),
                metrics.favoriteFileCount(),
                metrics.shareDownloadCount(),
                metrics.activeTaskCount(),
                metrics.dailyActiveUsers(),
                metrics.requestTimeline(),
                identityAdminSummaryApi.currentInviteCode()
        );
    }

    public PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery) {
        PageResponse<WorkspaceAdminFileView> response = workspaceAdminGovernanceApi.listFilesAsAdmin(
                new WorkspaceAdminFileQuery(
                        page,
                        size,
                        normalizeQuery(query),
                        normalizeQuery(ownerQuery)
                )
        );
        return new PageResponse<>(
                response.items().stream().map(this::toFileResponse).toList(),
                response.total(),
                response.page(),
                response.size()
        );
    }

    public PageResponse<AdminFileBlobResponse> listFileBlobs(int page,
                                                             int size,
                                                             String userQuery,
                                                             Long storagePolicyId,
                                                             String objectKey,
                                                             AdminFileEntityType entityType) {
        PageResponse<ContentAdminFileBlobView> response = contentAdminInspectionApi.listFileBlobsAsAdmin(
                new ContentAdminFileBlobQuery(
                        page,
                        size,
                        normalizeQuery(userQuery),
                        storagePolicyId,
                        normalizeQuery(objectKey),
                        toContentEntityType(entityType)
                )
        );
        return new PageResponse<>(
                response.items().stream().map(this::toFileBlobResponse).toList(),
                response.total(),
                response.page(),
                response.size()
        );
    }

    public PageResponse<AdminShareResponse> listShares(int page,
                                                       int size,
                                                       String userQuery,
                                                       String fileName,
                                                       String token,
                                                       Boolean passwordProtected,
                                                       Boolean expired) {
        PageResponse<SharingAdminShareView> response = sharingApi.listSharesAsAdmin(
                new SharingAdminShareQuery(
                        page,
                        size,
                        normalizeQuery(userQuery),
                        normalizeQuery(fileName),
                        normalizeQuery(token),
                        passwordProtected,
                        expired
                )
        );
        return new PageResponse<>(
                response.items().stream().map(this::toAdminShareResponse).toList(),
                response.total(),
                response.page(),
                response.size()
        );
    }

    private AdminFileResponse toFileResponse(WorkspaceAdminFileView file) {
        return new AdminFileResponse(
                file.fileId(),
                file.filename(),
                file.path(),
                file.size(),
                file.contentType(),
                file.directory(),
                file.createdAt(),
                file.ownerUserId(),
                file.ownerUsername(),
                file.ownerEmail(),
                file.favorite(),
                file.thumbnailAvailable()
        );
    }

    private AdminFileBlobResponse toFileBlobResponse(ContentAdminFileBlobView view) {
        return new AdminFileBlobResponse(
                view.entityId(),
                view.blobId(),
                view.objectKey(),
                view.entityType() == null ? null : AdminFileEntityType.valueOf(view.entityType().name()),
                view.storagePolicyId(),
                view.size(),
                view.contentType(),
                view.referenceCount(),
                view.linkedStoredFileCount(),
                view.linkedOwnerCount(),
                view.sampleOwnerUsername(),
                view.sampleOwnerEmail(),
                view.createdByUserId(),
                view.createdByUsername(),
                view.createdAt(),
                view.blobCreatedAt(),
                view.blobMissing(),
                view.orphanRisk(),
                view.referenceMismatch()
        );
    }

    private AdminShareResponse toAdminShareResponse(SharingAdminShareView share) {
        return new AdminShareResponse(
                share.id(),
                share.token(),
                share.shareName(),
                share.passwordProtected(),
                share.expired(),
                share.createdAt(),
                share.expiresAt(),
                share.maxDownloads(),
                share.downloadCount(),
                share.viewCount(),
                share.allowImport(),
                share.allowDownload(),
                share.ownerUserId(),
                share.ownerUsername(),
                share.ownerEmail(),
                share.fileId(),
                share.fileName(),
                share.filePath(),
                share.fileContentType(),
                share.fileSize(),
                share.directory()
        );
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private ContentEntityType toContentEntityType(AdminFileEntityType type) {
        if (type == null) {
            return null;
        }
        return ContentEntityType.valueOf(type.name());
    }
}
