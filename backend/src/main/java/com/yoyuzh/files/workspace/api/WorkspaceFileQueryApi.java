package com.yoyuzh.files.workspace.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface WorkspaceFileQueryApi {

    Optional<WorkspaceFileSnapshot> findOwnedActiveFile(Long userId, Long fileId);

    Optional<WorkspaceFileSnapshot> findActiveFile(Long fileId);

    Map<Long, WorkspaceFileSnapshot> findActiveFilesByIds(Set<Long> fileIds);

    long sumFileSizeByUserId(Long userId);
}
