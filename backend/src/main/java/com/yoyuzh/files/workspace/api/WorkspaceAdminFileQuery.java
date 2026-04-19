package com.yoyuzh.files.workspace.api;

public record WorkspaceAdminFileQuery(
        int page,
        int size,
        String query,
        String ownerQuery
) {
}
