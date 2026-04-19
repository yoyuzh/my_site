package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ExternalImportRulesService {

    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final FileUploadRulesService fileUploadRulesService;

    ExternalImportRulesService(WorkspaceNodeRulesService workspaceNodeRulesService,
                               FileUploadRulesService fileUploadRulesService) {
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.fileUploadRulesService = fileUploadRulesService;
    }

    List<String> normalizeDirectories(List<String> directories) {
        if (directories == null || directories.isEmpty()) {
            return List.of();
        }
        return directories.stream()
                .map(workspaceNodeRulesService::normalizeDirectoryPath)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo))
                .toList();
    }

    List<FileService.ExternalFileImport> normalizeFiles(List<FileService.ExternalFileImport> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(file -> new FileService.ExternalFileImport(
                        workspaceNodeRulesService.normalizeDirectoryPath(file.path()),
                        workspaceNodeRulesService.normalizeLeafName(file.filename()),
                        StringUtils.hasText(file.contentType()) ? file.contentType().trim() : "application/octet-stream",
                        file.content() == null ? new byte[0] : file.content()
                ))
                .toList();
    }

    void validateBatch(User recipient,
                       List<String> directories,
                       List<FileService.ExternalFileImport> files) {
        fileUploadRulesService.ensureWithinStorageQuota(recipient, files.stream().mapToLong(FileService.ExternalFileImport::size).sum());

        Set<String> plannedTargets = new LinkedHashSet<>();
        for (String directory : directories) {
            if ("/".equals(directory)) {
                continue;
            }
            if (!plannedTargets.add(directory)) {
                continue;
            }
            String parentPath = workspaceNodeRulesService.extractParentPath(directory);
            String directoryName = workspaceNodeRulesService.extractLeafName(directory);
            workspaceNodeRulesService.ensureNodeNameAvailable(recipient.getId(), parentPath, directoryName, "解压目标已存在");
        }

        for (FileService.ExternalFileImport file : files) {
            String logicalPath = workspaceNodeRulesService.buildTargetLogicalPath(file.path(), file.filename());
            if (plannedTargets.contains(logicalPath) || !plannedTargets.add(logicalPath)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "解压目标已存在");
            }
            workspaceNodeRulesService.ensureNodeNameAvailable(recipient.getId(), file.path(), file.filename(), "同目录下文件已存在");
        }
    }
}
