package com.yoyuzh.transfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TransferFileItem(
        @NotBlank(message = "文件名不能为空")
        String name,
        @Min(value = 0, message = "文件大小不能为负数")
        long size,
        String contentType
) {
}
