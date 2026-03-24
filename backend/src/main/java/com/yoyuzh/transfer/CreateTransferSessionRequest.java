package com.yoyuzh.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateTransferSessionRequest(
        @NotNull(message = "传输模式不能为空")
        TransferMode mode,
        @NotEmpty(message = "至少选择一个文件")
        List<@Valid TransferFileItem> files
) {
}
