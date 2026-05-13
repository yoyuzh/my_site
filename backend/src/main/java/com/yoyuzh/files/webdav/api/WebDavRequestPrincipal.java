package com.yoyuzh.files.webdav.api;

public record WebDavRequestPrincipal(
        Long userId,
        String username,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
}
