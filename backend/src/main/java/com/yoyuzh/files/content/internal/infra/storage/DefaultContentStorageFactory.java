package com.yoyuzh.files.content.internal.infra.storage;

import com.yoyuzh.files.content.api.ContentStorageFactory;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultContentStorageFactory implements ContentStorageFactory {

    @Override
    public FileContentStorage create(StorageRuntimeProperties properties) {
        String provider = properties.getProvider() == null ? "local" : properties.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "s3" -> new S3FileContentStorage(properties);
            case "oss" -> new OssSdkFileContentStorage(properties);
            case "webdav" -> new WebDavFileContentStorage(properties);
            default -> new LocalFileContentStorage(properties);
        };
    }
}
