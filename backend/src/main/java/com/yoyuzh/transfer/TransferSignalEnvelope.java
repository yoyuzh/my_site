package com.yoyuzh.transfer;

public record TransferSignalEnvelope(
        long cursor,
        String type,
        String payload
) {
}
