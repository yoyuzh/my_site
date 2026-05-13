package com.yoyuzh.files.workspace.api;

import java.io.InputStream;

public record WorkspaceDownloadStreamResult(
        String filename,
        String contentType,
        InputStream content,
        long contentLength
) {
}
