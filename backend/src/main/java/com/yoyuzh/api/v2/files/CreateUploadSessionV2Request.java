package com.yoyuzh.api.v2.files;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateUploadSessionV2Request(
        @NotBlank String path,
        @NotBlank String filename,
        String contentType,
        @Min(0) long size
) {
}
