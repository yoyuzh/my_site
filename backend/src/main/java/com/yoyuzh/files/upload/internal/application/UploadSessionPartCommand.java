package com.yoyuzh.files.upload.internal.application;

public record UploadSessionPartCommand(
        String etag,
        long size
) {
}
