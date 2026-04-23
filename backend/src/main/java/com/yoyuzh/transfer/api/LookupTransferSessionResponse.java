package com.yoyuzh.transfer.api;

import java.time.Instant;

public record LookupTransferSessionResponse(
        String sessionId,
        String pickupCode,
        TransferMode mode,
        Instant expiresAt
) {
}
