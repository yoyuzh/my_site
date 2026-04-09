package com.yoyuzh.files.core;

import jakarta.validation.constraints.NotBlank;

public record MoveFileRequest(
        @NotBlank(message = "目标路径不能为空")
        String path
) {
}
