package com.yoyuzh.files.core;

import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpFileListDirectoryCacheService implements FileListDirectoryCacheService {

    @Override
    public PageResponse<FileMetadataResponse> getOrLoad(Long userId,
                                                        String path,
                                                        int page,
                                                        int size,
                                                        Supplier<PageResponse<FileMetadataResponse>> loader) {
        return loader.get();
    }

    @Override
    public void touchDirectories(Long userId, Collection<String> paths) {
    }
}
