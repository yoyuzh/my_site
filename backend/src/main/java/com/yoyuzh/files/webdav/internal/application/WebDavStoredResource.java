package com.yoyuzh.files.webdav.internal.application;

import java.time.Instant;

public record WebDavStoredResource(
        String path,
        String name,
        boolean directory,
        long contentLength,
        String contentType,
        Instant createdAt,
        Instant lastModifiedAt,
        String etag
) {
}
