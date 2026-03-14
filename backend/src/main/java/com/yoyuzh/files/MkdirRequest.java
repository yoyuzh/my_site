package com.yoyuzh.files;

import jakarta.validation.constraints.NotBlank;

public record MkdirRequest(@NotBlank String path) {
}
