package com.yoyuzh.files.content.api;

import java.io.InputStream;

public interface ContentBlobReadApi {

    ContentBlobReadResult readBlob(Long blobId, boolean directory);

    ContentBlobReadResult readBlob(ContentBlobReference blobReference);

    boolean isBlobReady(Long blobId, boolean directory);
}
