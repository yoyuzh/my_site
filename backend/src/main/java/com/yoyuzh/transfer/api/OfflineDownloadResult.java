package com.yoyuzh.transfer.api;

import org.springframework.core.io.InputStreamSource;

public record OfflineDownloadResult(
        boolean redirect,
        String redirectUrl,
        String filename,
        String contentType,
        Long contentLength,
        InputStreamSource body
) {

    public static OfflineDownloadResult redirect(String redirectUrl) {
        return new OfflineDownloadResult(true, redirectUrl, null, null, null, null);
    }

    public static OfflineDownloadResult inline(String filename,
                                               String contentType,
                                               long contentLength,
                                               InputStreamSource body) {
        return new OfflineDownloadResult(false, null, filename, contentType, contentLength, body);
    }
}
