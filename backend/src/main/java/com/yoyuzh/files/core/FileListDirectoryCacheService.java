package com.yoyuzh.files.core;

import com.yoyuzh.common.PageResponse;

import java.util.Collection;
import java.util.function.Supplier;

public interface FileListDirectoryCacheService {

    PageResponse<FileMetadataResponse> getOrLoad(Long userId,
                                                 String path,
                                                 int page,
                                                 int size,
                                                 Supplier<PageResponse<FileMetadataResponse>> loader);

    void touchDirectories(Long userId, Collection<String> paths);

    default void touchDirectory(Long userId, String path) {
        touchDirectories(userId, java.util.List.of(path));
    }

    static FileListDirectoryCacheService noOp() {
        return NoOpHolder.INSTANCE;
    }

    final class NoOpHolder {
        private static final FileListDirectoryCacheService INSTANCE = new FileListDirectoryCacheService() {
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
        };

        private NoOpHolder() {
        }
    }
}
