package com.yoyuzh.transfer.api;

import com.yoyuzh.transfer.TransferFileItem;
import com.yoyuzh.transfer.TransferMode;

import java.util.List;

public record CreateTransferSessionCommand(
        TransferMode mode,
        List<TransferFileItem> files
) {
}
