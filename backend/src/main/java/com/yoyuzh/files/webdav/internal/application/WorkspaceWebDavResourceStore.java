package com.yoyuzh.files.webdav.internal.application;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.files.workspace.api.WebDavWorkspacePutCommand;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadStreamResult;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspacePathDownloadApi;
import com.yoyuzh.files.workspace.api.WorkspacePathNodeApi;
import com.yoyuzh.files.workspace.api.WorkspacePathWriteApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class WorkspaceWebDavResourceStore implements WebDavResourceStore {

    private static final int WEBDAV_DIRECTORY_PAGE_SIZE = 1000;

    private final WorkspacePathNodeApi workspacePathNodeApi;
    private final WorkspacePathDownloadApi workspacePathDownloadApi;
    private final WorkspacePathWriteApi workspacePathWriteApi;

    public WorkspaceWebDavResourceStore(WorkspacePathNodeApi workspacePathNodeApi,
                                        WorkspacePathDownloadApi workspacePathDownloadApi,
                                        WorkspacePathWriteApi workspacePathWriteApi) {
        this.workspacePathNodeApi = workspacePathNodeApi;
        this.workspacePathDownloadApi = workspacePathDownloadApi;
        this.workspacePathWriteApi = workspacePathWriteApi;
    }

    @Override
    public Optional<WebDavStoredResource> find(WebDavPrincipal principal, String path) {
        return workspacePathNodeApi.findOwnedActiveNodeByPath(principal.userId(), path)
                .map(this::toResource);
    }

    @Override
    public List<WebDavStoredResource> list(WebDavPrincipal principal, String directoryPath) {
        List<WebDavStoredResource> resources = new java.util.ArrayList<>();
        int page = 0;
        PageResponse<FileMetadataResponse> children;
        do {
            children = workspacePathNodeApi.listOwnedDirectory(
                    principal.userId(),
                    directoryPath,
                    page,
                    WEBDAV_DIRECTORY_PAGE_SIZE
            );
            resources.addAll(children.items().stream()
                    .map(child -> toResource(toSnapshot(principal.userId(), child)))
                    .toList());
            page++;
        } while (resources.size() < children.total() && !children.items().isEmpty());
        return resources;
    }

    @Override
    public WebDavReadResult read(WebDavPrincipal principal, String path) {
        WorkspaceDownloadStreamResult result = workspacePathDownloadApi.streamOwnedFileByPath(principal.userId(), path);
        return new WebDavReadResult(result.contentType(), result.content(), result.contentLength());
    }

    @Override
    public void write(WebDavPrincipal principal,
                      String path,
                      String contentType,
                      long size,
                      InputStream content,
                      boolean overwrite) {
        workspacePathWriteApi.putFileByPath(new WebDavWorkspacePutCommand(
                new WorkspaceUserContext(
                        principal.userId(),
                        principal.storageQuotaBytes(),
                        principal.maxUploadSizeBytes()
                ),
                path,
                contentType,
                size,
                content,
                overwrite
        ));
    }

    @Override
    public void createDirectory(WebDavPrincipal principal, String path) {
        workspacePathWriteApi.createDirectoryByPath(principal.userId(), path);
    }

    @Override
    public void copy(WebDavPrincipal principal, String fromPath, String toPath, boolean overwrite) {
        workspacePathWriteApi.copyByPath(principal.userId(), fromPath, toPath, overwrite, bytes -> { });
    }

    @Override
    public void move(WebDavPrincipal principal, String fromPath, String toPath, boolean overwrite) {
        workspacePathWriteApi.moveByPath(principal.userId(), fromPath, toPath, overwrite);
    }

    @Override
    public void delete(WebDavPrincipal principal, String path) {
        // Workspace lifecycle owns recursive directory recycle semantics.
        workspacePathWriteApi.recycleByPath(principal.userId(), path);
    }

    private WebDavStoredResource toResource(WorkspaceFileSnapshot snapshot) {
        String path = buildLogicalPath(snapshot.path(), snapshot.filename());
        Instant createdAt = toInstant(snapshot.createdAt());
        boolean root = "/".equals(path);
        return new WebDavStoredResource(
                path,
                root ? "dav" : snapshot.filename(),
                snapshot.directory(),
                snapshot.size() == null ? 0L : snapshot.size(),
                snapshot.contentType(),
                createdAt,
                createdAt,
                root ? "\"root-" + snapshot.userId() + "\"" : "\"" + snapshot.id() + "-" + (snapshot.blobId() == null ? 0L : snapshot.blobId()) + "\""
        );
    }

    private WorkspaceFileSnapshot toSnapshot(Long userId, FileMetadataResponse response) {
        LocalDateTime createdAt = response.createdAt() == null ? response.updatedAt() : response.createdAt();
        return new WorkspaceFileSnapshot(
                response.id(),
                userId,
                response.filename(),
                response.path(),
                response.size(),
                response.contentType(),
                response.directory(),
                null,
                createdAt
        );
    }

    private String buildLogicalPath(String parentPath, String filename) {
        if (parentPath == null || parentPath.isBlank() || "/".equals(parentPath)) {
            return filename == null || filename.isBlank() ? "/" : "/" + filename;
        }
        return parentPath + "/" + filename;
    }

    private Instant toInstant(LocalDateTime time) {
        LocalDateTime safeTime = time == null ? LocalDateTime.now() : time;
        return safeTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
