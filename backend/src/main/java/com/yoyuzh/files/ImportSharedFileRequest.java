package com.yoyuzh.files;

import jakarta.validation.constraints.NotBlank;

public record ImportSharedFileRequest(@NotBlank String path) {
}
