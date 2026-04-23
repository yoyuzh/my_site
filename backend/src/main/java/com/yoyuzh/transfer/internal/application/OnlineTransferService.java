package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.transfer.api.CreateTransferSessionCommand;
import com.yoyuzh.transfer.api.LookupTransferSessionResponse;
import com.yoyuzh.transfer.api.PollTransferSignalsResponse;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.api.TransferSessionResponse;
import com.yoyuzh.transfer.api.TransferSignalRequest;
import com.yoyuzh.transfer.internal.domain.TransferRole;
import com.yoyuzh.transfer.internal.domain.TransferSession;
import com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository;
import com.yoyuzh.transfer.internal.infra.TransferSessionStore;
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
    private static final int PICKUP_CODE_COLLISION_RETRY_LIMIT = 32;

    private final TransferSessionStore sessionStore;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;

    public TransferSessionResponse createSession(CreateTransferSessionCommand command) {
        String sessionId = UUID.randomUUID().toString();
        String pickupCode = nextPickupCode();
        Instant expiresAt = Instant.now().plus(ONLINE_SESSION_TTL);
        List<TransferFileItem> files = command.files().stream()
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
        for (int attempt = 0; attempt < PICKUP_CODE_COLLISION_RETRY_LIMIT; attempt++) {
            String pickupCode = sessionStore.nextPickupCode();
            if (!offlineTransferSessionRepository.existsByPickupCode(pickupCode)) {
                return pickupCode;
            }
        }
        throw new BusinessException(ErrorCode.UNKNOWN, "unable to allocate pickup code");
    }

    private TransferFileItem normalizeOnlineFileItem(TransferFileItem file) {
        String normalizedFilename = TransferPathNormalizer.normalizeLeafName(file.name());
        String normalizedRelativePath = TransferPathNormalizer.normalizeRelativePath(file.relativePath(), normalizedFilename);
        return new TransferFileItem(
                null,
                normalizedFilename,
                normalizedRelativePath,
                file.size(),
                TransferPathNormalizer.normalizeContentType(file.contentType()),
                null
        );
    }
}
