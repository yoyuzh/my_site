package com.yoyuzh.files.content.api;

public record MultipartCompletedPart(
        int partNumber,
        String etag
) {
}
