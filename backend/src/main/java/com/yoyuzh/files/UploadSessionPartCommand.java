package com.yoyuzh.files;

public record UploadSessionPartCommand(
        String etag,
        long size
) {
}
