package com.yoyuzh.transfer.api;

public record TransferSignalEnvelope(
        long cursor,
        String type,
        String payload
) {
}
