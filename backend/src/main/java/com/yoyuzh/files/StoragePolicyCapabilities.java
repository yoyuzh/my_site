package com.yoyuzh.files;

public record StoragePolicyCapabilities(
        boolean directUpload,
        boolean multipartUpload,
        boolean signedDownloadUrl,
        boolean serverProxyDownload,
        boolean thumbnailNative,
        boolean friendlyDownloadName,
        boolean requiresCors,
        boolean supportsInternalEndpoint,
        long maxObjectSize
) {
}
