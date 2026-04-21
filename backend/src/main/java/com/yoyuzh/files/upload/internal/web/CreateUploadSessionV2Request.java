package com.yoyuzh.files.upload.internal.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateUploadSessionV2Request(
        @NotBlank String path,
        @NotBlank String filename,
        String contentType,
        @Min(0) long size
) {
}
