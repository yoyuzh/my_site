package com.yoyuzh.transfer;

import jakarta.validation.constraints.NotBlank;

public record TransferSignalRequest(
        @NotBlank(message = "信令类型不能为空")
        String type,
        @NotBlank(message = "信令内容不能为空")
        String payload
) {
}
