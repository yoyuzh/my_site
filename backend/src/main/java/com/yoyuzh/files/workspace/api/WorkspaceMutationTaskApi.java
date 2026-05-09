package com.yoyuzh.files.workspace.api;

import java.util.List;

public interface WorkspaceMutationTaskApi {

    WorkspaceMutationTaskView enqueueRename(Long userId, Long fileId, String filename);

    WorkspaceMutationTaskView enqueueMove(Long userId,
                                          List<Long> fileIds,
                                          String targetPath,
                                          WorkspaceMoveConflictStrategy conflictStrategy);

    WorkspaceMutationTaskView enqueueDelete(Long userId, List<Long> fileIds, FileDeleteMode mode);
}
