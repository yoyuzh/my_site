package com.yoyuzh.config;

import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.LocalFileContentStorage;
import com.yoyuzh.files.storage.OssFileContentStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfiguration {

    @Bean
    public FileContentStorage fileContentStorage(FileStorageProperties properties) {
        if ("oss".equalsIgnoreCase(properties.getProvider())) {
            return new OssFileContentStorage(properties);
        }
        return new LocalFileContentStorage(properties);
    }
}
