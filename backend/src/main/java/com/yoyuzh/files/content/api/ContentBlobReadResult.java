package com.yoyuzh.files.content.api;

import java.io.InputStream;

public record ContentBlobReadResult(
        ContentBlobReference blob,
        InputStream content,
        long contentLength,
        boolean pendingFallback
) {
}
