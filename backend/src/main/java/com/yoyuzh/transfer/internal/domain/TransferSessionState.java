package com.yoyuzh.transfer.internal.domain;

import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferSignalEnvelope;
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
