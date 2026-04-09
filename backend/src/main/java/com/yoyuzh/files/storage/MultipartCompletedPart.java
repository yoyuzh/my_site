package com.yoyuzh.files.storage;

public record MultipartCompletedPart(
        int partNumber,
        String etag
) {
}
