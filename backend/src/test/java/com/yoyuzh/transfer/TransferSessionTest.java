package com.yoyuzh.transfer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSessionTest {

    @Test
    void shouldEmitPeerJoinedOnlyOnceWhenReceiverJoinsRepeatedly() {
        TransferSession session = new TransferSession(
                "session-1",
                "849201",
                Instant.parse("2026-03-20T12:00:00Z"),
                List.of(new TransferFileItem("report.pdf", 2048, "application/pdf"))
        );

        session.markReceiverJoined();
        session.markReceiverJoined();

        PollTransferSignalsResponse senderSignals = session.poll(TransferRole.SENDER, 0);

        assertThat(senderSignals.items())
                .extracting(TransferSignalEnvelope::type)
                .containsExactly("peer-joined");
        assertThat(senderSignals.nextCursor()).isEqualTo(1);
    }

    @Test
    void shouldRouteSignalsToTheOppositeRoleQueue() {
        TransferSession session = new TransferSession(
                "session-1",
                "849201",
                Instant.parse("2026-03-20T12:00:00Z"),
                List.of(new TransferFileItem("report.pdf", 2048, "application/pdf"))
        );

        session.enqueue(TransferRole.SENDER, "offer", "{\"sdp\":\"demo-offer\"}");
        session.enqueue(TransferRole.RECEIVER, "answer", "{\"sdp\":\"demo-answer\"}");

        PollTransferSignalsResponse receiverSignals = session.poll(TransferRole.RECEIVER, 0);
        PollTransferSignalsResponse senderSignals = session.poll(TransferRole.SENDER, 0);

        assertThat(receiverSignals.items())
                .extracting(TransferSignalEnvelope::type, TransferSignalEnvelope::payload)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("offer", "{\"sdp\":\"demo-offer\"}"));
        assertThat(senderSignals.items())
                .extracting(TransferSignalEnvelope::type, TransferSignalEnvelope::payload)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("answer", "{\"sdp\":\"demo-answer\"}"));
    }
}
