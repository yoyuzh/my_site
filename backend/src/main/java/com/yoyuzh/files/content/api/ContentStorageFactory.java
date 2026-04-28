package com.yoyuzh.files.content.api;

import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;

@FunctionalInterface
public interface ContentStorageFactory {

    FileContentStorage create(StorageRuntimeProperties properties);
}
