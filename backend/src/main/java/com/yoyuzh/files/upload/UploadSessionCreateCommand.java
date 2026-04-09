package com.yoyuzh.files.upload;

public record UploadSessionCreateCommand(
        String path,
        String filename,
        String contentType,
        long size
) {
}
