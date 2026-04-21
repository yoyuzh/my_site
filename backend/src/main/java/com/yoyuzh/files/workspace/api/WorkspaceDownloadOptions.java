package com.yoyuzh.files.workspace.api;

public record WorkspaceDownloadOptions(
        String packageDownloadBaseUrl,
        String packageDownloadSecret,
        long packageDownloadTtlSeconds
) {

    public static WorkspaceDownloadOptions disabled() {
        return new WorkspaceDownloadOptions(null, null, 300L);
    }
}
