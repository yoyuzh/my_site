package com.yoyuzh.platform.storage.internal.application;

import com.yoyuzh.platform.storage.api.StorageRuntimeLimitApi;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeStorageRuntimeLimitApi implements StorageRuntimeLimitApi {

    private final FileStorageProperties fileStorageProperties;

    @Override
    public long maxFileSizeBytes() {
        return fileStorageProperties.getMaxFileSize();
    }
}
