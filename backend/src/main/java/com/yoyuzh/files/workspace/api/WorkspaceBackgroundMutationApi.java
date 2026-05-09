package com.yoyuzh.files.workspace.api;

import java.util.List;

public interface WorkspaceBackgroundMutationApi {

    FileMetadataResponse rename(Long userId, Long fileId, String nextFilename);

    WorkspaceMoveResult move(Long userId,
                             Long fileId,
                             String nextPath,
                             WorkspaceMoveConflictStrategy conflictStrategy);

    WorkspaceMoveResult batchMove(Long userId,
                                  List<Long> fileIds,
                                  String nextPath,
                                  WorkspaceMoveConflictStrategy conflictStrategy);

    void delete(Long userId, Long fileId, FileDeleteMode mode);

    void batchDelete(Long userId, List<Long> fileIds, FileDeleteMode mode);
}
