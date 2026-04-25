package com.yoyuzh.files.workspace.internal.domain;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class WorkspaceRecycleLifecycle {

    private static final String RECYCLE_BIN_PATH_PREFIX = "/.recycle";

    public void moveToRecycleBin(List<StoredFile> filesToRecycle, Long recycleRootId, LocalDateTime deletedAt) {
        if (filesToRecycle.isEmpty()) {
            return;
        }

        StoredFile recycleRoot = filesToRecycle.stream()
                .filter(item -> recycleRootId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        String recycleGroupId = UUID.randomUUID().toString().replace("-", "");
        String rootLogicalPath = recycleRoot.logicalPath();
        String recycleRootPath = buildRecycleBinPath(recycleGroupId, recycleRoot.getPath());
        String recycleRootLogicalPath = buildLogicalPath(recycleRootPath, recycleRoot.getFilename());

        List<StoredFile> orderedItems = filesToRecycle.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile item) -> item.logicalPath().length())
                        .thenComparing(item -> item.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .toList();

        for (StoredFile item : orderedItems) {
            String originalPath = item.getPath();
            String recyclePath = recycleRootId.equals(item.getId())
                    ? recycleRootPath
                    : remapDescendantPath(item.getPath(), rootLogicalPath, recycleRootLogicalPath);
            item.recycleTo(recyclePath, originalPath, recycleGroupId, recycleRootId.equals(item.getId()), deletedAt);
        }
    }

    public void restoreFromRecycleBin(List<StoredFile> recycleGroupItems) {
        for (StoredFile item : recycleGroupItems) {
            item.restoreFromRecycleBin();
        }
    }

    private String buildRecycleBinPath(String recycleGroupId, String originalPath) {
        if ("/".equals(originalPath)) {
            return RECYCLE_BIN_PATH_PREFIX + "/" + recycleGroupId;
        }
        return RECYCLE_BIN_PATH_PREFIX + "/" + recycleGroupId + originalPath;
    }

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path) ? "/" + filename : path + "/" + filename;
    }

    private String remapDescendantPath(String currentPath, String oldLogicalPath, String newLogicalPath) {
        if (currentPath.equals(oldLogicalPath)) {
            return newLogicalPath;
        }
        return newLogicalPath + currentPath.substring(oldLogicalPath.length());
    }
}
