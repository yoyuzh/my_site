package com.yoyuzh.transfer;

import java.time.Instant;
import java.util.List;

public record TransferSessionResponse(
        String sessionId,
        String pickupCode,
        Instant expiresAt,
        List<TransferFileItem> files
) {
}
