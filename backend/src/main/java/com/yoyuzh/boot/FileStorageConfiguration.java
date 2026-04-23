package com.yoyuzh.boot;

import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.LocalFileContentStorage;
import com.yoyuzh.files.storage.S3FileContentStorage;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfiguration {

    @Bean
    public FileContentStorage fileContentStorage(StorageRuntimeProperties properties) {
        if ("s3".equalsIgnoreCase(properties.getProvider())) {
            return new S3FileContentStorage(properties);
        }
        return new LocalFileContentStorage(properties);
    }
}
