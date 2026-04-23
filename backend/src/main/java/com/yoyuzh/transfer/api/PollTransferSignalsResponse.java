package com.yoyuzh.transfer.api;

import java.util.List;

public record PollTransferSignalsResponse(
        List<TransferSignalEnvelope> items,
        long nextCursor
) {
}
