package com.yoyuzh.files.workspace.api;

import java.util.List;

public interface WorkspaceBootstrapApi {

    void ensureDefaultDirectories(WorkspaceUserContext user);

    boolean existsNode(WorkspaceUserContext user, String path, String filename);

    FileMetadataResponse importExternalFile(WorkspaceUserContext user,
                                            String path,
                                            String filename,
                                            String contentType,
                                            long size,
                                            byte[] content);

    void importExternalFilesAtomically(WorkspaceUserContext user,
                                       List<String> directories,
                                       List<WorkspaceExternalFileImport> files,
                                       WorkspaceExternalImportProgressListener progressListener);
}
