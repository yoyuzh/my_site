package com.yoyuzh.files;

import jakarta.validation.constraints.NotBlank;

public record RenameFileRequest(
        @NotBlank(message = "文件名不能为空")
        String filename
) {
}
