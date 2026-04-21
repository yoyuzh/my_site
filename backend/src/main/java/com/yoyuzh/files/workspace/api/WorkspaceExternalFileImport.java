package com.yoyuzh.files.workspace.api;

public record WorkspaceExternalFileImport(
        String path,
        String filename,
        String contentType,
        byte[] content
) {
    public long size() {
        return content == null ? 0L : content.length;
    }
}
