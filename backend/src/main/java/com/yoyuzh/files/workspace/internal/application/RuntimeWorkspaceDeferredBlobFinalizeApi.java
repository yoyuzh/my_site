package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceDeferredBlobFinalizeApi;
import org.springframework.stereotype.Service;

@Service
public class RuntimeWorkspaceDeferredBlobFinalizeApi implements WorkspaceDeferredBlobFinalizeApi {

    private final WorkspaceFileIngressService workspaceFileIngressService;
    private final RuntimeWorkspaceContentRegistrationApi runtimeWorkspaceContentRegistrationApi;

    public RuntimeWorkspaceDeferredBlobFinalizeApi(WorkspaceFileIngressService workspaceFileIngressService,
                                                   RuntimeWorkspaceContentRegistrationApi runtimeWorkspaceContentRegistrationApi) {
        this.workspaceFileIngressService = workspaceFileIngressService;
        this.runtimeWorkspaceContentRegistrationApi = runtimeWorkspaceContentRegistrationApi;
    }

    @Override
    public FinalizedReplacement finalizeDeferredReplace(Long userId,
                                                        Long fileId,
                                                        Long blobId,
                                                        String contentType,
                                                        long size) {
        return workspaceFileIngressService.finalizeDeferredReplace(userId, fileId, blobId, contentType, size);
    }

    @Override
    public void deletePendingTempFile(String localTempPath) {
        workspaceFileIngressService.deletePendingTempFile(localTempPath);
    }

    @Override
    public void finalizeReplace(Long userId,
                                Long targetFileId,
                                String contentType,
                                long size,
                                Long newBlobId,
                                String newObjectKey,
                                Long newPrimaryEntityId) {
        runtimeWorkspaceContentRegistrationApi.finalizeReplace(
                userId,
                targetFileId,
                contentType,
                size,
                newBlobId,
                newObjectKey,
                newPrimaryEntityId
        );
    }
}
