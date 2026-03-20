package com.yoyuzh.transfer;

import java.util.List;

public record PollTransferSignalsResponse(
        List<TransferSignalEnvelope> items,
        long nextCursor
) {
}
