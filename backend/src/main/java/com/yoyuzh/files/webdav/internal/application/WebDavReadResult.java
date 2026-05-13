package com.yoyuzh.files.webdav.internal.application;

import java.io.InputStream;

public record WebDavReadResult(
        String contentType,
        InputStream content,
        long contentLength
) {
}
