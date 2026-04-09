package com.yoyuzh.files.core;

import jakarta.validation.constraints.NotBlank;

public record MkdirRequest(@NotBlank String path) {
}
