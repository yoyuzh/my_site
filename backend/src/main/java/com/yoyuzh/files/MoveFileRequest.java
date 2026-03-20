package com.yoyuzh.files;

import jakarta.validation.constraints.NotBlank;

public record MoveFileRequest(
        @NotBlank(message = "目标路径不能为空")
        String path
) {
}
