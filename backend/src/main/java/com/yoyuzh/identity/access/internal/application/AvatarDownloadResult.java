package com.yoyuzh.identity.access.internal.application;

public record AvatarDownloadResult(
        boolean redirect,
        String redirectUrl,
        String filename,
        String contentType,
        byte[] body
) {

    public static AvatarDownloadResult redirect(String redirectUrl) {
        return new AvatarDownloadResult(true, redirectUrl, null, null, null);
    }

    public static AvatarDownloadResult inline(String filename, String contentType, byte[] body) {
        return new AvatarDownloadResult(false, null, filename, contentType, body);
    }
}
