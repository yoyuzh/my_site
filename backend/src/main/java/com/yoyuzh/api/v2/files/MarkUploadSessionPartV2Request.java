package com.yoyuzh.api.v2.files;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MarkUploadSessionPartV2Request(
        @NotBlank String etag,
        @Min(0) long size
) {
}
