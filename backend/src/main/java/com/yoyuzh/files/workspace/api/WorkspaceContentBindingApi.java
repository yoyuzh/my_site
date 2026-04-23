package com.yoyuzh.files.workspace.api;

import java.util.List;

public interface WorkspaceContentBindingApi {

    List<WorkspaceContentBindingFile> findFilesMissingBlobBindings();

    void attachBlob(Long fileId, Long blobId);

    List<WorkspaceContentBindingFile> findFilesMissingPrimaryEntityBindings();

    void attachPrimaryEntity(Long fileId, Long primaryEntityId);

    long countFilesByBlobId(Long blobId);
}
