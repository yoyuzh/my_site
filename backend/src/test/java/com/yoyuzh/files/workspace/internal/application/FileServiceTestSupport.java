package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentBlobRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.internal.application.ContentBlobLifecycleService;
import com.yoyuzh.files.content.internal.application.RuntimeContentAssetApi;
import com.yoyuzh.files.content.internal.application.RuntimeContentBlobQueryApi;
import com.yoyuzh.files.content.internal.application.RuntimeContentBlobRegistrationApi;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.internal.application.RuntimeUploadCompletionApi;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadMetricsPort;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.files.workspace.internal.infra.FileListDirectoryCacheService;

import java.time.Clock;

final class FileServiceTestSupport {

    private FileServiceTestSupport() {
    }

    static WorkspaceUserContext workspaceUser(User user) {
        return new WorkspaceUserContext(
                user.getId(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    static FileService create(StoredFileRepository storedFileRepository,
                              FileBlobRepository fileBlobRepository,
                              FileContentStorage fileContentStorage,
                              WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                              WorkspaceDownloadOptions workspaceDownloadOptions,
                              long maxFileSize) {
        return create(
                storedFileRepository,
                fileBlobRepository,
                null,
                null,
                fileContentStorage,
                null,
                workspaceDownloadMetricsPort,
                workspaceDownloadOptions,
                maxFileSize,
                Clock.systemUTC()
        );
    }

    static FileService create(StoredFileRepository storedFileRepository,
                              FileBlobRepository fileBlobRepository,
                              FileContentStorage fileContentStorage,
                              WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                              WorkspaceDownloadOptions workspaceDownloadOptions,
                              long maxFileSize,
                              Clock clock) {
        return create(
                storedFileRepository,
                fileBlobRepository,
                null,
                null,
                fileContentStorage,
                null,
                workspaceDownloadMetricsPort,
                workspaceDownloadOptions,
                maxFileSize,
                clock
        );
    }

    static FileService create(StoredFileRepository storedFileRepository,
                              FileBlobRepository fileBlobRepository,
                              FileEntityRepository fileEntityRepository,
                              StoredFileEntityRepository storedFileEntityRepository,
                              FileContentStorage fileContentStorage,
                              StoragePolicyQuery storagePolicyQuery,
                              WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                              WorkspaceDownloadOptions workspaceDownloadOptions,
                              long maxFileSize) {
        return create(
                storedFileRepository,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                fileContentStorage,
                storagePolicyQuery,
                workspaceDownloadMetricsPort,
                workspaceDownloadOptions,
                maxFileSize,
                Clock.systemUTC()
        );
    }

    private static FileService create(StoredFileRepository storedFileRepository,
                                      FileBlobRepository fileBlobRepository,
                                      FileEntityRepository fileEntityRepository,
                                      StoredFileEntityRepository storedFileEntityRepository,
                                      FileContentStorage fileContentStorage,
                                      StoragePolicyQuery storagePolicyQuery,
                                      WorkspaceDownloadMetricsPort workspaceDownloadMetricsPort,
                                      WorkspaceDownloadOptions workspaceDownloadOptions,
                                      long maxFileSize,
                                      Clock clock) {
        RuntimeWorkspacePathPolicy workspacePathPolicy = new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
        WorkspaceNodeRulesService workspaceNodeRulesService = new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy);
        WorkspaceDirectoryApi workspaceDirectoryApi = new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage, workspacePathPolicy);
        WorkspaceMutationApi workspaceMutationApi = new RuntimeWorkspaceMutationApi(storedFileRepository, workspacePathPolicy);
        RuntimeWorkspaceContentBindingApi workspaceContentBindingApi = new RuntimeWorkspaceContentBindingApi(storedFileRepository);
        ContentAssetApi contentAssetApi = new RuntimeContentAssetApi(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
        RuntimeWorkspaceContentRegistrationApi contentRegistrationApi = new RuntimeWorkspaceContentRegistrationApi(
                storedFileRepository,
                contentAssetApi
        );
        ContentBlobQueryApi contentBlobQueryApi = new RuntimeContentBlobQueryApi(fileBlobRepository);
        WorkspaceLifecycleApi workspaceLifecycleApi = new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                contentRegistrationApi,
                contentBlobQueryApi,
                workspacePathPolicy,
                workspaceNodeRulesService
        );
        FileUploadRulesService fileUploadRulesService = new FileUploadRulesService(
                storedFileRepository,
                storagePolicyQuery,
                (UploadConstraintPolicy) null,
                workspaceNodeRulesService,
                maxFileSize
        );
        ExternalImportRulesService externalImportRulesService = new ExternalImportRulesService(
                workspaceNodeRulesService,
                fileUploadRulesService
        );
        ContentBlobRegistrationApi contentBlobRegistrationApi = new RuntimeContentBlobRegistrationApi(fileBlobRepository);
        ContentBlobLifecycleApi contentBlobLifecycleApi = new ContentBlobLifecycleService(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileContentStorage
        );
        UploadCompletionApi uploadCompletionApi = new RuntimeUploadCompletionApi(
                workspacePathPolicy,
                contentRegistrationApi,
                contentBlobRegistrationApi,
                fileContentStorage
        );
        WorkspaceFileIngressService workspaceFileIngressService = new WorkspaceFileIngressService(
                fileContentStorage,
                contentAssetApi,
                contentRegistrationApi,
                contentBlobRegistrationApi,
                uploadCompletionApi,
                contentBlobLifecycleApi,
                fileUploadRulesService,
                workspaceNodeRulesService
        );
        WorkspaceFileActivityService workspaceFileActivityService = new WorkspaceFileActivityService(
                workspaceNodeRulesService,
                null,
                null,
                FileListDirectoryCacheService.noOp()
        );
        return new FileService(
                storedFileRepository,
                fileContentStorage,
                workspaceDownloadOptions,
                workspaceNodeRulesService,
                workspaceDirectoryApi,
                workspaceMutationApi,
                workspaceLifecycleApi,
                fileUploadRulesService,
                externalImportRulesService,
                contentBlobLifecycleApi,
                workspaceDownloadMetricsPort,
                FileListDirectoryCacheService.noOp(),
                workspaceFileIngressService,
                workspaceFileActivityService,
                DistributedLockGateway.noOp(),
                maxFileSize,
                clock
        );
    }
}
