package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.search.api.FileEventApi;
import com.yoyuzh.files.search.api.FileEventRecordCommand;
import com.yoyuzh.files.search.api.FileEventType;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.FileListDirectoryCacheService;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceFileActivityService {

    private final WorkspaceNodeRulesService workspaceNodeRulesService;
    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private final FileEventApi fileEventApi;
    private final FileListDirectoryCacheService fileListDirectoryCacheService;

    @Autowired
    public WorkspaceFileActivityService(WorkspaceNodeRulesService workspaceNodeRulesService,
                                        ObjectProvider<BackgroundTaskLifecycleApi> backgroundTaskLifecycleApi,
                                        ObjectProvider<FileEventApi> fileEventApi,
                                        ObjectProvider<FileListDirectoryCacheService> fileListDirectoryCacheService) {
        this(
                workspaceNodeRulesService,
                backgroundTaskLifecycleApi.getIfAvailable(),
                fileEventApi.getIfAvailable(),
                fileListDirectoryCacheService.getIfAvailable(FileListDirectoryCacheService::noOp)
        );
    }

    WorkspaceFileActivityService(WorkspaceNodeRulesService workspaceNodeRulesService,
                                 BackgroundTaskLifecycleApi backgroundTaskLifecycleApi,
                                 FileEventApi fileEventApi,
                                 FileListDirectoryCacheService fileListDirectoryCacheService) {
        this.workspaceNodeRulesService = workspaceNodeRulesService;
        this.backgroundTaskLifecycleApi = backgroundTaskLifecycleApi;
        this.fileEventApi = fileEventApi;
        this.fileListDirectoryCacheService = fileListDirectoryCacheService == null
                ? FileListDirectoryCacheService.noOp()
                : fileListDirectoryCacheService;
    }

    public void touchDirectories(WorkspaceUserContext user, String... paths) {
        if (user == null || user.userId() == null || paths == null || paths.length == 0) {
            return;
        }

        List<String> affectedPaths = new ArrayList<>();
        for (String path : paths) {
            if (StringUtils.hasText(path)) {
                affectedPaths.add(workspaceNodeRulesService.normalizeDirectoryPath(path));
            }
        }
        if (!affectedPaths.isEmpty()) {
            fileListDirectoryCacheService.touchDirectories(user.userId(), affectedPaths);
        }
    }

    public void afterFileCreated(WorkspaceUserContext user, String normalizedPath, RegisteredContentFile storedFile) {
        touchDirectories(user, normalizedPath);
        queueAutoMediaMetadata(user, storedFile);
        recordFileEvent(user, FileEventType.CREATED, storedFile, null, buildLogicalPath(storedFile.path(), storedFile.filename()));
    }

    public void recordMutation(WorkspaceUserContext user,
                               FileEventType eventType,
                               StoredFile storedFile,
                               String fromPath,
                               String toPath) {
        if (fileEventApi == null || user == null || user.userId() == null || storedFile == null || storedFile.getId() == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", eventType.name());
        payload.put("fileId", storedFile.getId());
        payload.put("filename", storedFile.getFilename());
        payload.put("path", storedFile.getPath());
        payload.put("directory", storedFile.isDirectory());
        payload.put("contentType", storedFile.getContentType());
        payload.put("size", storedFile.getSize());
        if (fromPath != null) {
            payload.put("fromPath", fromPath);
        }
        if (toPath != null) {
            payload.put("toPath", toPath);
        }
        fileEventApi.record(new FileEventRecordCommand(
                user.userId(),
                eventType,
                storedFile.getId(),
                fromPath,
                toPath,
                null,
                payload
        ));
    }

    public void recordMutation(WorkspaceUserContext user,
                               FileEventType eventType,
                               FileMetadataResponse storedFile,
                               String fromPath,
                               String toPath) {
        if (fileEventApi == null || user == null || user.userId() == null || storedFile == null || storedFile.id() == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", eventType.name());
        payload.put("fileId", storedFile.id());
        payload.put("filename", storedFile.filename());
        payload.put("path", storedFile.path());
        payload.put("directory", storedFile.directory());
        payload.put("contentType", storedFile.contentType());
        payload.put("size", storedFile.size());
        if (fromPath != null) {
            payload.put("fromPath", fromPath);
        }
        if (toPath != null) {
            payload.put("toPath", toPath);
        }
        fileEventApi.record(new FileEventRecordCommand(
                user.userId(),
                eventType,
                storedFile.id(),
                fromPath,
                toPath,
                null,
                payload
        ));
    }

    private void queueAutoMediaMetadata(WorkspaceUserContext user, RegisteredContentFile storedFile) {
        if (backgroundTaskLifecycleApi == null) {
            return;
        }
        backgroundTaskLifecycleApi.createQueuedAutoMediaMetadataTask(user.userId(), storedFile.id(), null);
    }

    private void recordFileEvent(WorkspaceUserContext user,
                                 FileEventType eventType,
                                 RegisteredContentFile storedFile,
                                 String fromPath,
                                 String toPath) {
        if (fileEventApi == null || user == null || user.userId() == null || storedFile == null || storedFile.id() == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", eventType.name());
        payload.put("fileId", storedFile.id());
        payload.put("filename", storedFile.filename());
        payload.put("path", storedFile.path());
        payload.put("directory", storedFile.directory());
        payload.put("contentType", storedFile.contentType());
        payload.put("size", storedFile.size());
        if (fromPath != null) {
            payload.put("fromPath", fromPath);
        }
        if (toPath != null) {
            payload.put("toPath", toPath);
        }
        fileEventApi.record(new FileEventRecordCommand(
                user.userId(),
                eventType,
                storedFile.id(),
                fromPath,
                toPath,
                null,
                payload
        ));
    }

    private String buildLogicalPath(String path, String filename) {
        return "/".equals(path) ? "/" + filename : path + "/" + filename;
    }
}
