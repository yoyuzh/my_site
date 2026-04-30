package com.yoyuzh.files.upload.internal.application;

public record UploadSessionCreateCommand(
        String path,
        String filename,
        String contentType,
        long size
) {
}
