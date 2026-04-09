package com.yoyuzh.files.share;

import jakarta.validation.constraints.NotBlank;

public record ImportSharedFileRequest(@NotBlank String path) {
}
