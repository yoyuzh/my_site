package com.yoyuzh.files;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CompleteUploadRequest(
        @NotBlank String path,
        @NotBlank String filename,
        @NotBlank String storageName,
        String contentType,
        @Min(0) long size
) {
}
