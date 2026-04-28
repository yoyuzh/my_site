package com.yoyuzh.files.content.internal.infra.storage;

import com.yoyuzh.files.content.api.ContentStorageFactory;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultContentStorageFactory implements ContentStorageFactory {

    @Override
    public FileContentStorage create(StorageRuntimeProperties properties) {
        if ("s3".equalsIgnoreCase(properties.getProvider())) {
            return new S3FileContentStorage(properties);
        }
        return new LocalFileContentStorage(properties);
    }
}
