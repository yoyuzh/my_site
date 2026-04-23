package com.yoyuzh.files.content.api;

import java.util.List;
import java.util.function.Supplier;

public interface ContentBlobLifecycleApi {

    <T> T executeAfterBlobStored(String objectKey, Supplier<T> operation);

    void cleanupWrittenBlobs(List<String> writtenBlobObjectKeys, RuntimeException ex);

    ContentBlobReference requireBlobReference(Long blobId, boolean directory);

    List<ContentBlobReference> collectBlobReferencesToDelete(List<Long> blobIdsToDelete);

    void deleteBlobReferences(List<ContentBlobReference> blobsToDelete);
}
