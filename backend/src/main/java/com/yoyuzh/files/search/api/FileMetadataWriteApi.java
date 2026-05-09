package com.yoyuzh.files.search.api;

public interface FileMetadataWriteApi {

    void upsertPublicMetadata(Long fileId, String name, String value);
}
