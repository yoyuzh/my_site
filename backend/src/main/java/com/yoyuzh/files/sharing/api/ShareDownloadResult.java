package com.yoyuzh.files.sharing.api;

public record ShareDownloadResult(
        boolean redirect,
        String redirectUrl,
        String filename,
        String contentType,
        byte[] body
) {

    public static ShareDownloadResult redirect(String redirectUrl) {
        return new ShareDownloadResult(true, redirectUrl, null, null, null);
    }

    public static ShareDownloadResult inline(String filename, String contentType, byte[] body) {
        return new ShareDownloadResult(false, null, filename, contentType, body);
    }
}
