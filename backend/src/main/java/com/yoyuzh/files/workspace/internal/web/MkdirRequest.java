package com.yoyuzh.files.workspace.internal.web;

import jakarta.validation.constraints.NotBlank;

public record MkdirRequest(@NotBlank String path) {
}
