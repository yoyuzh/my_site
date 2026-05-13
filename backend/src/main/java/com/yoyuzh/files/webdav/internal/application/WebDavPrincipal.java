package com.yoyuzh.files.webdav.internal.application;

public record WebDavPrincipal(
        Long userId,
        String username,
        long storageQuotaBytes,
        long maxUploadSizeBytes
) {
}
