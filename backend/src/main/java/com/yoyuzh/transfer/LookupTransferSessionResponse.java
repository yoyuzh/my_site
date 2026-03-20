package com.yoyuzh.transfer;

import java.time.Instant;

public record LookupTransferSessionResponse(
        String sessionId,
        String pickupCode,
        Instant expiresAt
) {
}
