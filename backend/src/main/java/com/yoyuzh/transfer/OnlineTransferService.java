package com.yoyuzh.transfer;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnlineTransferService {

    private static final Duration ONLINE_SESSION_TTL = Duration.ofMinutes(15);

    private final TransferSessionStore sessionStore;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;

    public TransferSessionResponse createSession(CreateTransferSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String pickupCode = nextPickupCode();
        Instant expiresAt = Instant.now().plus(ONLINE_SESSION_TTL);
        List<TransferFileItem> files = request.files().stream()
                .map(this::normalizeOnlineFileItem)
                .toList();

        TransferSession session = new TransferSession(sessionId, pickupCode, expiresAt, files);
        sessionStore.save(session);
        return session.toSessionResponse();
    }

    public LookupTransferSessionResponse lookupSession(String pickupCode) {
        TransferSession onlineSession = sessionStore.findByPickupCode(pickupCode).orElse(null);
        return onlineSession == null ? null : onlineSession.toLookupResponse();
    }

    public TransferSessionResponse joinSession(String sessionId) {
        return sessionStore.withSession(sessionId, onlineSession -> {
            try {
                onlineSession.markReceiverJoined();
            } catch (IllegalStateException ex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "online transfer session can only be joined once");
            }
            sessionStore.save(onlineSession);
            return onlineSession.toSessionResponse();
        });
    }

    public boolean postSignal(String sessionId, String role, TransferSignalRequest request) {
        Boolean handled = sessionStore.withSession(sessionId, session -> {
            session.enqueue(TransferRole.from(role), request.type().trim(), request.payload().trim());
            sessionStore.save(session);
            return true;
        });
        return Boolean.TRUE.equals(handled);
    }

    public PollTransferSignalsResponse pollSignals(String sessionId, String role, long after) {
        TransferSession session = sessionStore.findById(sessionId).orElse(null);
        if (session == null) {
            return null;
        }
        return session.poll(TransferRole.from(role), Math.max(0, after));
    }

    public void pruneExpiredSessions(Instant now) {
        sessionStore.pruneExpired(now);
    }

    private String nextPickupCode() {
        String pickupCode;
        do {
            pickupCode = sessionStore.nextPickupCode();
        } while (offlineTransferSessionRepository.existsByPickupCode(pickupCode));
        return pickupCode;
    }

    private TransferFileItem normalizeOnlineFileItem(TransferFileItem file) {
        String normalizedFilename = normalizeLeafName(file.name());
        String normalizedRelativePath = normalizeRelativePath(file.relativePath(), normalizedFilename);
        return new TransferFileItem(
                null,
                normalizedFilename,
                normalizedRelativePath,
                file.size(),
                normalizeContentType(file.contentType()),
                null
        );
    }

    private String normalizeContentType(String contentType) {
        String normalized = Objects.requireNonNullElse(contentType, "").trim();
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }

    private String normalizeLeafName(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "file name cannot be empty");
        }
        if (normalized.contains("/") || normalized.contains("\\") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "invalid file name");
        }
        return normalized;
    }

    private String normalizeRelativePath(String relativePath, String fallbackFilename) {
        String rawPath = Objects.requireNonNullElse(relativePath, fallbackFilename).replace('\\', '/');
        List<String> segments = new ArrayList<>();
        for (String segment : rawPath.split("/")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (".".equals(trimmed) || "..".equals(trimmed)) {
                throw new BusinessException(ErrorCode.UNKNOWN, "invalid file path");
            }
            segments.add(trimmed);
        }

        String normalizedFilename = normalizeLeafName(fallbackFilename);
        if (segments.isEmpty()) {
            return normalizedFilename;
        }

        List<String> normalizedSegments = new ArrayList<>(segments.subList(0, Math.max(0, segments.size() - 1)));
        normalizedSegments.add(normalizedFilename);
        return String.join("/", normalizedSegments);
    }
}
