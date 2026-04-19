package com.yoyuzh.files.workspace.api;

import java.util.Optional;
import com.yoyuzh.shared.kernel.PageResponse;

public interface WorkspaceAdminGovernanceApi {

    Optional<WorkspaceAdminFileSnapshot> deleteFileAsAdmin(Long fileId);

    PageResponse<WorkspaceAdminFileView> listFilesAsAdmin(WorkspaceAdminFileQuery query);

    long countFilesAsAdmin();
}
