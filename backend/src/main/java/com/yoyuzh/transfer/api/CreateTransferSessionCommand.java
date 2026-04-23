package com.yoyuzh.transfer.api;

import java.util.List;

public record CreateTransferSessionCommand(
        TransferMode mode,
        List<TransferFileItem> files
) {
}
