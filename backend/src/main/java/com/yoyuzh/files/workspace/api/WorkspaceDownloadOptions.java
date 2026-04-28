package com.yoyuzh.files.workspace.api;

public record WorkspaceDownloadOptions(
        String publicDownloadBaseUrl,
        String packageDownloadBaseUrl,
        String packageDownloadSecret,
        long packageDownloadTtlSeconds
) {

    public static WorkspaceDownloadOptions disabled() {
        return new WorkspaceDownloadOptions(null, null, null, 300L);
    }
}
