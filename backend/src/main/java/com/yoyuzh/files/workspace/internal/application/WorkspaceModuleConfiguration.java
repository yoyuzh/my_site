package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentBlobQueryApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkspaceModuleConfiguration {

    @Bean
    WorkspaceNodeRulesService workspaceNodeRulesService(RuntimeWorkspacePathPolicy workspacePathPolicy) {
        return new WorkspaceNodeRulesService(workspacePathPolicy, workspacePathPolicy);
    }

    @Bean
    WorkspaceDirectoryApi workspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                                FileContentStorage fileContentStorage,
                                                RuntimeWorkspacePathPolicy workspacePathPolicy,
                                                WorkspaceRequestProbe workspaceRequestProbe) {
        return new RuntimeWorkspaceDirectoryApi(
                storedFileRepository,
                fileContentStorage,
                workspacePathPolicy,
                workspaceRequestProbe
        );
    }

    @Bean
    WorkspaceMutationApi workspaceMutationApi(StoredFileRepository storedFileRepository,
                                              RuntimeWorkspacePathPolicy workspacePathPolicy) {
        return new RuntimeWorkspaceMutationApi(storedFileRepository, workspacePathPolicy);
    }

    @Bean
    WorkspaceLifecycleApi workspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                                ContentDuplicationApi contentDuplicationApi,
                                                ContentBlobQueryApi contentBlobQueryApi,
                                                RuntimeWorkspacePathPolicy workspacePathPolicy,
                                                WorkspaceNodeRulesService workspaceNodeRulesService) {
        return new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                contentDuplicationApi,
                contentBlobQueryApi,
                workspacePathPolicy,
                workspaceNodeRulesService
        );
    }

    @Bean
    FileUploadRulesService fileUploadRulesService(StoredFileRepository storedFileRepository,
                                                  StoragePolicyQuery storagePolicyQuery,
                                                  UploadConstraintPolicy uploadConstraintPolicy,
                                                  WorkspaceNodeRulesService workspaceNodeRulesService,
                                                  StorageRuntimeProperties storageRuntimeProperties) {
        return new FileUploadRulesService(
                storedFileRepository,
                storagePolicyQuery,
                uploadConstraintPolicy,
                workspaceNodeRulesService,
                storageRuntimeProperties.getMaxFileSize()
        );
    }

    @Bean
    ExternalImportRulesService externalImportRulesService(WorkspaceNodeRulesService workspaceNodeRulesService,
                                                          FileUploadRulesService fileUploadRulesService) {
        return new ExternalImportRulesService(workspaceNodeRulesService, fileUploadRulesService);
    }

    @Bean
    WorkspaceDownloadOptions workspaceDownloadOptions(StorageRuntimeProperties storageRuntimeProperties) {
        return new WorkspaceDownloadOptions(
                storageRuntimeProperties.getS3().getPublicDownloadBaseUrl(),
                storageRuntimeProperties.getS3().getPackageDownloadBaseUrl(),
                storageRuntimeProperties.getS3().getPackageDownloadSecret(),
                Math.max(1, storageRuntimeProperties.getS3().getPackageDownloadTtlSeconds())
        );
    }
}
