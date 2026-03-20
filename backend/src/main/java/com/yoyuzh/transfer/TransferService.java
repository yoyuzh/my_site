package com.yoyuzh.transfer;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransferService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    private final TransferSessionStore sessionStore;

    public TransferService(TransferSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public TransferSessionResponse createSession(CreateTransferSessionRequest request) {
        pruneExpiredSessions();

        String sessionId = UUID.randomUUID().toString();
        String pickupCode = sessionStore.nextPickupCode();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        List<TransferFileItem> files = request.files().stream()
                .map(file -> new TransferFileItem(file.name(), file.size(), normalizeContentType(file.contentType())))
                .toList();

        TransferSession session = new TransferSession(sessionId, pickupCode, expiresAt, files);
        sessionStore.save(session);
        return session.toSessionResponse();
    }

    public LookupTransferSessionResponse lookupSession(String pickupCode) {
        pruneExpiredSessions();
        String normalizedPickupCode = normalizePickupCode(pickupCode);
        TransferSession session = sessionStore.findByPickupCode(normalizedPickupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "取件码不存在或已失效"));
        return session.toLookupResponse();
    }

    public TransferSessionResponse joinSession(String sessionId) {
        pruneExpiredSessions();
        TransferSession session = getRequiredSession(sessionId);
        session.markReceiverJoined();
        return session.toSessionResponse();
    }

    public void postSignal(String sessionId, String role, TransferSignalRequest request) {
        pruneExpiredSessions();
        TransferSession session = getRequiredSession(sessionId);
        session.enqueue(TransferRole.from(role), request.type().trim(), request.payload().trim());
    }

    public PollTransferSignalsResponse pollSignals(String sessionId, String role, long after) {
        pruneExpiredSessions();
        TransferSession session = getRequiredSession(sessionId);
        return session.poll(TransferRole.from(role), Math.max(0, after));
    }

    private TransferSession getRequiredSession(String sessionId) {
        TransferSession session = sessionStore.findById(sessionId).orElse(null);
        if (session == null || session.isExpired(Instant.now())) {
            if (session != null) {
                sessionStore.remove(session);
            }
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "快传会话不存在或已失效");
        }
        return session;
    }

    private void pruneExpiredSessions() {
        sessionStore.pruneExpired(Instant.now());
    }

    private String normalizePickupCode(String pickupCode) {
        String normalized = Objects.requireNonNullElse(pickupCode, "").replaceAll("\\D", "");
        if (normalized.length() != 6) {
            throw new BusinessException(ErrorCode.UNKNOWN, "取件码格式不正确");
        }
        return normalized;
    }

    private String normalizeContentType(String contentType) {
        String normalized = Objects.requireNonNullElse(contentType, "").trim();
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }
}
