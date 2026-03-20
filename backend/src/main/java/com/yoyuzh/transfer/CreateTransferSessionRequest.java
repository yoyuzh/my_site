package com.yoyuzh.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateTransferSessionRequest(
        @NotEmpty(message = "至少选择一个文件")
        List<@Valid TransferFileItem> files
) {
}
