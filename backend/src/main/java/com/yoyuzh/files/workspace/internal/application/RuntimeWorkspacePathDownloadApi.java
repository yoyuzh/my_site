package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceDownloadResult;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadStreamResult;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspacePathDownloadApi;
import com.yoyuzh.files.workspace.api.WorkspacePathNodeApi;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeWorkspacePathDownloadApi implements WorkspacePathDownloadApi {

    private final WorkspacePathNodeApi workspacePathNodeApi;
    private final FileService fileService;

    public RuntimeWorkspacePathDownloadApi(WorkspacePathNodeApi workspacePathNodeApi, FileService fileService) {
        this.workspacePathNodeApi = workspacePathNodeApi;
        this.fileService = fileService;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDownloadResult downloadOwnedFileByPath(Long userId, String normalizedLogicalPath) {
        WorkspaceFileSnapshot file = requireDownloadableFile(userId, normalizedLogicalPath);
        return fileService.download(userId, file.id());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDownloadStreamResult streamOwnedFileByPath(Long userId, String normalizedLogicalPath) {
        WorkspaceFileSnapshot file = requireDownloadableFile(userId, normalizedLogicalPath);
        return fileService.downloadStream(userId, file.id());
    }

    private WorkspaceFileSnapshot requireDownloadableFile(Long userId, String normalizedLogicalPath) {
        WorkspaceFileSnapshot file = workspacePathNodeApi.findOwnedActiveNodeByPath(userId, normalizedLogicalPath)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (file.directory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "目录不能作为文件下载");
        }
        return file;
    }
}
