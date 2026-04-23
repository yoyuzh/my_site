package com.yoyuzh.files.content.api;

public interface ContentBlobRegistrationApi {

    ContentBlobReference registerStoredBlob(String objectKey, String contentType, long size);
}
