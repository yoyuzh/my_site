package com.yoyuzh.transfer;

import java.time.Instant;
import java.util.List;

public record TransferSessionState(
        String sessionId,
        String pickupCode,
        Instant expiresAt,
        List<TransferFileItem> files,
        List<TransferSignalEnvelope> senderQueue,
        List<TransferSignalEnvelope> receiverQueue,
        boolean receiverJoined,
        long nextSenderCursor,
        long nextReceiverCursor
) {
}
