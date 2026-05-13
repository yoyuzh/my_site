package com.yoyuzh.files.workspace.api;

import java.io.InputStream;

public record WebDavWorkspacePutCommand(
        WorkspaceUserContext user,
        String normalizedLogicalPath,
        String contentType,
        long size,
        InputStream content,
        boolean overwrite
) {
}
