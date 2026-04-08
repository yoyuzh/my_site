package com.yoyuzh.files;

public record UploadSessionCreateCommand(
        String path,
        String filename,
        String contentType,
        long size
) {
}
