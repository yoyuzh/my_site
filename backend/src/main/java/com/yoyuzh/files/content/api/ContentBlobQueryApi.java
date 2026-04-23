package com.yoyuzh.files.content.api;

import java.util.Optional;

public interface ContentBlobQueryApi {

    Optional<ContentBlobReference> findBlobReferenceById(Long blobId);
}
