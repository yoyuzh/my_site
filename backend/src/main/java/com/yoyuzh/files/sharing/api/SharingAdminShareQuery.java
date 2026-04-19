package com.yoyuzh.files.sharing.api;

public record SharingAdminShareQuery(
        int page,
        int size,
        String userQuery,
        String fileName,
        String token,
        Boolean passwordProtected,
        Boolean expired
) {
}
