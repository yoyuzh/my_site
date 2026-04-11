package com.yoyuzh.admin;

public record AdminFilesystemResponse(
        OverviewSection overview,
        AdminStoragePolicyResponse defaultPolicy,
        UploadSection upload,
        MediaProcessingSection mediaProcessing,
        CacheSection cache,
        WebdavSection webdav
) {

    public record OverviewSection(
            String storageProvider,
            long totalFiles,
            long totalBlobs,
            long totalEntities
    ) {
    }

    public record UploadSection(
            boolean proxyUpload,
            boolean directSingleUpload,
            boolean directMultipartUpload,
            long effectiveMaxFileSizeBytes
    ) {
    }

    public record MediaProcessingSection(
            boolean metadataExtractionEnabled,
            boolean nativeThumbnailSupport
    ) {
    }

    public record CacheSection(
            String backend,
            long filesListTtlSeconds,
            long directoryVersionTtlSeconds
    ) {
    }

    public record WebdavSection(
            boolean enabled
    ) {
    }
}
