package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspacePathNodeApi;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RuntimeWorkspacePathNodeApi implements WorkspacePathNodeApi {

    private final StoredFileRepository storedFileRepository;
    private final WorkspacePathPolicy workspacePathPolicy;
    private final WorkspaceDirectoryApi workspaceDirectoryApi;

    public RuntimeWorkspacePathNodeApi(StoredFileRepository storedFileRepository,
                                       WorkspacePathPolicy workspacePathPolicy,
                                       WorkspaceDirectoryApi workspaceDirectoryApi) {
        this.storedFileRepository = storedFileRepository;
        this.workspacePathPolicy = workspacePathPolicy;
        this.workspaceDirectoryApi = workspaceDirectoryApi;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceFileSnapshot> findOwnedActiveNodeByPath(Long userId, String normalizedLogicalPath) {
        String logicalPath = workspacePathPolicy.normalizeDirectoryPath(normalizedLogicalPath);
        if ("/".equals(logicalPath)) {
            return Optional.of(rootSnapshot(userId));
        }
        String parentPath = workspacePathPolicy.extractParentPath(logicalPath);
        String leafName = workspacePathPolicy.extractLeafName(logicalPath);
        return storedFileRepository.findByUserIdAndPathAndFilename(userId, parentPath, leafName)
                .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileMetadataResponse> listOwnedDirectory(Long userId,
                                                                 String normalizedDirectoryPath,
                                                                 int page,
                                                                 int size) {
        String directoryPath = workspacePathPolicy.normalizeDirectoryPath(normalizedDirectoryPath);
        return workspaceDirectoryApi.loadDirectoryPage(userId, directoryPath, page, size);
    }

    private WorkspaceFileSnapshot rootSnapshot(Long userId) {
        return new WorkspaceFileSnapshot(
                null,
                userId,
                "",
                "/",
                0L,
                "directory",
                true,
                null,
                LocalDateTime.of(1970, 1, 1, 0, 0)
        );
    }

    private WorkspaceFileSnapshot toSnapshot(StoredFile file) {
        return new WorkspaceFileSnapshot(
                file.getId(),
                file.getUserId(),
                file.getFilename(),
                file.getPath(),
                file.getSize(),
                file.getContentType(),
                file.isDirectory(),
                file.getBlobId(),
                file.getCreatedAt()
        );
    }
}
