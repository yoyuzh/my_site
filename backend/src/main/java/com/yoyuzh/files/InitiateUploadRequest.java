package com.yoyuzh.files;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record InitiateUploadRequest(
        @NotBlank String path,
        @NotBlank String filename,
        String contentType,
        @Min(0) long size
) {
}
