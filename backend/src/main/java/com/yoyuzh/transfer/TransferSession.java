package com.yoyuzh.transfer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class TransferSession {

    private final String sessionId;
    private final String pickupCode;
    private final Instant expiresAt;
    private final List<TransferFileItem> files;
    private final List<TransferSignalEnvelope> senderQueue = new ArrayList<>();
    private final List<TransferSignalEnvelope> receiverQueue = new ArrayList<>();
    private boolean receiverJoined;
    private long nextSenderCursor = 1;
    private long nextReceiverCursor = 1;

    TransferSession(String sessionId, String pickupCode, Instant expiresAt, List<TransferFileItem> files) {
        this.sessionId = sessionId;
        this.pickupCode = pickupCode;
        this.expiresAt = expiresAt;
        this.files = List.copyOf(files);
    }

    synchronized TransferSessionResponse toSessionResponse() {
        return new TransferSessionResponse(sessionId, pickupCode, expiresAt, files);
    }

    synchronized LookupTransferSessionResponse toLookupResponse() {
        return new LookupTransferSessionResponse(sessionId, pickupCode, expiresAt);
    }

    synchronized void markReceiverJoined() {
        if (receiverJoined) {
            return;
        }

        receiverJoined = true;
        senderQueue.add(new TransferSignalEnvelope(nextSenderCursor++, "peer-joined", "{}"));
    }

    synchronized void enqueue(TransferRole sourceRole, String type, String payload) {
        if (sourceRole == TransferRole.SENDER) {
            receiverQueue.add(new TransferSignalEnvelope(nextReceiverCursor++, type, payload));
            return;
        }

        senderQueue.add(new TransferSignalEnvelope(nextSenderCursor++, type, payload));
    }

    synchronized PollTransferSignalsResponse poll(TransferRole role, long after) {
        List<TransferSignalEnvelope> queue = role == TransferRole.SENDER ? senderQueue : receiverQueue;
        List<TransferSignalEnvelope> items = queue.stream()
                .filter(item -> item.cursor() > after)
                .toList();
        long nextCursor = items.isEmpty() ? after : items.get(items.size() - 1).cursor();
        return new PollTransferSignalsResponse(items, nextCursor);
    }

    boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    String sessionId() {
        return sessionId;
    }

    String pickupCode() {
        return pickupCode;
    }
}
