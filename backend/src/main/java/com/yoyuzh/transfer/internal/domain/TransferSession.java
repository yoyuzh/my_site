package com.yoyuzh.transfer.internal.domain;

import com.yoyuzh.transfer.api.LookupTransferSessionResponse;
import com.yoyuzh.transfer.api.PollTransferSignalsResponse;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferMode;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import com.yoyuzh.transfer.api.TransferSignalEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TransferSession {

    private final String sessionId;
    private final String pickupCode;
    private final Instant expiresAt;
    private final List<TransferFileItem> files;
    private final List<TransferSignalEnvelope> senderQueue = new ArrayList<>();
    private final List<TransferSignalEnvelope> receiverQueue = new ArrayList<>();
    private boolean receiverJoined;
    private long nextSenderCursor = 1;
    private long nextReceiverCursor = 1;

    public TransferSession(String sessionId, String pickupCode, Instant expiresAt, List<TransferFileItem> files) {
        this.sessionId = sessionId;
        this.pickupCode = pickupCode;
        this.expiresAt = expiresAt;
        this.files = List.copyOf(files);
    }

    public synchronized TransferSessionResponse toSessionResponse() {
        return new TransferSessionResponse(sessionId, pickupCode, TransferMode.ONLINE, expiresAt, files);
    }

    public synchronized LookupTransferSessionResponse toLookupResponse() {
        return new LookupTransferSessionResponse(sessionId, pickupCode, TransferMode.ONLINE, expiresAt);
    }

    public synchronized void markReceiverJoined() {
        if (receiverJoined) {
            throw new IllegalStateException("在线快传仅支持一次接收");
        }

        receiverJoined = true;
        senderQueue.add(new TransferSignalEnvelope(nextSenderCursor++, "peer-joined", "{}"));
    }

    public synchronized void enqueue(TransferRole sourceRole, String type, String payload) {
        if (sourceRole == TransferRole.SENDER) {
            receiverQueue.add(new TransferSignalEnvelope(nextReceiverCursor++, type, payload));
            return;
        }

        senderQueue.add(new TransferSignalEnvelope(nextSenderCursor++, type, payload));
    }

    public synchronized PollTransferSignalsResponse poll(TransferRole role, long after) {
        List<TransferSignalEnvelope> queue = role == TransferRole.SENDER ? senderQueue : receiverQueue;
        List<TransferSignalEnvelope> items = queue.stream()
                .filter(item -> item.cursor() > after)
                .toList();
        long nextCursor = items.isEmpty() ? after : items.get(items.size() - 1).cursor();
        return new PollTransferSignalsResponse(items, nextCursor);
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public String sessionId() {
        return sessionId;
    }

    public String pickupCode() {
        return pickupCode;
    }

    public synchronized TransferSessionState toState() {
        return new TransferSessionState(
                sessionId,
                pickupCode,
                expiresAt,
                files,
                List.copyOf(senderQueue),
                List.copyOf(receiverQueue),
                receiverJoined,
                nextSenderCursor,
                nextReceiverCursor
        );
    }

    public static TransferSession fromState(TransferSessionState state) {
        TransferSession session = new TransferSession(
                state.sessionId(),
                state.pickupCode(),
                state.expiresAt(),
                state.files()
        );
        synchronized (session) {
            session.senderQueue.addAll(state.senderQueue());
            session.receiverQueue.addAll(state.receiverQueue());
            session.receiverJoined = state.receiverJoined();
            session.nextSenderCursor = state.nextSenderCursor();
            session.nextReceiverCursor = state.nextReceiverCursor();
        }
        return session;
    }
}
