package com.yoyuzh.files.workspace.api;

import java.util.Optional;
import java.util.Map;
import java.util.Set;
import com.yoyuzh.shared.kernel.PageResponse;

public interface WorkspaceAdminGovernanceApi {

    Optional<WorkspaceAdminFileSnapshot> deleteFileAsAdmin(Long fileId);

    PageResponse<WorkspaceAdminFileView> listFilesAsAdmin(WorkspaceAdminFileQuery query);

    long countFilesAsAdmin();

    long loadUsedStorageBytesByUserId(Long userId);

    Map<Long, Long> loadUsedStorageBytesByUserIds(Set<Long> userIds);
}
