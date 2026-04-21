package com.yoyuzh.files.workspace.api;

public record WorkspaceDownloadResult(
        boolean redirect,
        String redirectUrl,
        String filename,
        String contentType,
        byte[] body
) {

    public static WorkspaceDownloadResult redirect(String redirectUrl) {
        return new WorkspaceDownloadResult(true, redirectUrl, null, null, null);
    }

    public static WorkspaceDownloadResult inline(String filename, String contentType, byte[] body) {
        return new WorkspaceDownloadResult(false, null, filename, contentType, body);
    }
}
