package com.yoyuzh.files.upload;

public record UploadSessionPartCommand(
        String etag,
        long size
) {
}
