package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceAdminMetricsApi;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeWorkspaceAdminMetricsApi implements WorkspaceAdminMetricsApi {

    private final StoredFileRepository storedFileRepository;

    @Override
    @Transactional(readOnly = true)
    public long countFavoriteFilesAsAdmin() {
        return storedFileRepository.countFavoriteFilesAsAdmin();
    }
}
