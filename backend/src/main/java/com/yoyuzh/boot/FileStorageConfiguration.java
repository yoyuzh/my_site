package com.yoyuzh.boot;

import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.LocalFileContentStorage;
import com.yoyuzh.files.storage.S3FileContentStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfiguration {

    @Bean
    public FileContentStorage fileContentStorage(FileStorageProperties properties) {
        if ("s3".equalsIgnoreCase(properties.getProvider())) {
            return new S3FileContentStorage(properties);
        }
        return new LocalFileContentStorage(properties);
    }
}
