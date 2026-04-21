package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi;
import com.yoyuzh.files.workspace.api.WorkspaceMutationApi;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.platform.storage.api.UploadConstraintPolicy;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkspaceModuleConfiguration {

    @Bean
    WorkspaceNodeRulesService workspaceNodeRulesService(RuntimeWorkspacePathPolicy workspacePathPolicy) {
        return new WorkspaceNodeRulesService(workspacePathPolicy);
    }

    @Bean
    WorkspaceDirectoryApi workspaceDirectoryApi(StoredFileRepository storedFileRepository,
                                                FileContentStorage fileContentStorage,
                                                RuntimeWorkspacePathPolicy workspacePathPolicy) {
        return new RuntimeWorkspaceDirectoryApi(storedFileRepository, fileContentStorage, workspacePathPolicy);
    }

    @Bean
    WorkspaceMutationApi workspaceMutationApi(StoredFileRepository storedFileRepository,
                                              RuntimeWorkspacePathPolicy workspacePathPolicy) {
        return new RuntimeWorkspaceMutationApi(storedFileRepository, workspacePathPolicy);
    }

    @Bean
    WorkspaceLifecycleApi workspaceLifecycleApi(StoredFileRepository storedFileRepository,
                                                ContentDuplicationApi contentDuplicationApi,
                                                RuntimeWorkspacePathPolicy workspacePathPolicy,
                                                WorkspaceNodeRulesService workspaceNodeRulesService) {
        return new RuntimeWorkspaceLifecycleApi(
                storedFileRepository,
                contentDuplicationApi,
                workspacePathPolicy,
                workspaceNodeRulesService
        );
    }

    @Bean
    FileUploadRulesService fileUploadRulesService(StoredFileRepository storedFileRepository,
                                                  StoragePolicyQuery storagePolicyQuery,
                                                  UploadConstraintPolicy uploadConstraintPolicy,
                                                  WorkspaceNodeRulesService workspaceNodeRulesService,
                                                  FileStorageProperties fileStorageProperties) {
        return new FileUploadRulesService(
                storedFileRepository,
                storagePolicyQuery,
                uploadConstraintPolicy,
                workspaceNodeRulesService,
                fileStorageProperties.getMaxFileSize()
        );
    }

    @Bean
    ExternalImportRulesService externalImportRulesService(WorkspaceNodeRulesService workspaceNodeRulesService,
                                                          FileUploadRulesService fileUploadRulesService) {
        return new ExternalImportRulesService(workspaceNodeRulesService, fileUploadRulesService);
    }

    @Bean
    WorkspaceDownloadOptions workspaceDownloadOptions(FileStorageProperties fileStorageProperties) {
        return new WorkspaceDownloadOptions(
                fileStorageProperties.getS3().getPackageDownloadBaseUrl(),
                fileStorageProperties.getS3().getPackageDownloadSecret(),
                Math.max(1, fileStorageProperties.getS3().getPackageDownloadTtlSeconds())
        );
    }
}
