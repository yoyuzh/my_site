package com.yoyuzh.boot;

import com.yoyuzh.files.content.api.ContentStorageFactory;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfiguration {

    @Bean
    public FileContentStorage fileContentStorage(StorageRuntimeProperties properties,
                                                 ContentStorageFactory contentStorageFactory) {
        return contentStorageFactory.create(properties);
    }
}
