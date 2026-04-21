package com.yoyuzh.files.sharing.api;

import jakarta.validation.constraints.NotBlank;

public record ImportSharedFileRequest(@NotBlank String path) {
}
