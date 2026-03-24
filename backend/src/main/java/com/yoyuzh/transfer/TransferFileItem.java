package com.yoyuzh.transfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TransferFileItem(
        String id,
        @NotBlank(message = "文件名不能为空")
        String name,
        String relativePath,
        @Min(value = 0, message = "文件大小不能为负数")
        long size,
        String contentType,
        Boolean uploaded
) {
    public TransferFileItem(String name, long size, String contentType) {
        this(null, name, name, size, contentType, null);
    }
}
