package com.yoyuzh.files.content.api;

public record ThumbnailResponse(
        Long fileId,
        boolean available,
        String url
) {

    public static ThumbnailResponse unavailable(Long fileId) {
        return new ThumbnailResponse(fileId, false, "");
    }
}
