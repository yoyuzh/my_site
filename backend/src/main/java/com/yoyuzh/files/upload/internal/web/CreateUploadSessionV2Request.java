package com.yoyuzh.files.upload.internal.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateUploadSessionV2Request(
        @NotBlank String path,
        @NotBlank String filename,
        @NotBlank String contentType,
        @Min(1) long size
) {
}
