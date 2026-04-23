package com.yoyuzh.transfer.api;

public record OfflineDownloadResult(
        boolean redirect,
        String redirectUrl,
        String filename,
        String contentType,
        byte[] body
) {

    public static OfflineDownloadResult redirect(String redirectUrl) {
        return new OfflineDownloadResult(true, redirectUrl, null, null, null);
    }

    public static OfflineDownloadResult inline(String filename, String contentType, byte[] body) {
        return new OfflineDownloadResult(false, null, filename, contentType, body);
    }
}
