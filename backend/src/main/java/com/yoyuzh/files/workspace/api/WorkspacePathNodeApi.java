package com.yoyuzh.files.workspace.api;

import com.yoyuzh.shared.kernel.PageResponse;

import java.util.Optional;

public interface WorkspacePathNodeApi {

    Optional<WorkspaceFileSnapshot> findOwnedActiveNodeByPath(Long userId, String normalizedLogicalPath);

    PageResponse<FileMetadataResponse> listOwnedDirectory(Long userId, String normalizedDirectoryPath, int page, int size);
}
