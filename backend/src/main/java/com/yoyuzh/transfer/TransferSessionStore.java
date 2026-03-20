package com.yoyuzh.transfer;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TransferSessionStore {

    private final Map<String, TransferSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdsByPickupCode = new ConcurrentHashMap<>();

    public void save(TransferSession session) {
        sessionsById.put(session.sessionId(), session);
        sessionIdsByPickupCode.put(session.pickupCode(), session.sessionId());
    }

    public Optional<TransferSession> findById(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public Optional<TransferSession> findByPickupCode(String pickupCode) {
        String sessionId = sessionIdsByPickupCode.get(pickupCode);
        if (sessionId == null) {
            return Optional.empty();
        }

        return findById(sessionId);
    }

    public void remove(TransferSession session) {
        sessionsById.remove(session.sessionId(), session);
        sessionIdsByPickupCode.remove(session.pickupCode(), session.sessionId());
    }

    public void pruneExpired(Instant now) {
        for (TransferSession session : sessionsById.values()) {
            if (session.isExpired(now)) {
                remove(session);
            }
        }
    }

    public String nextPickupCode() {
        String pickupCode;
        do {
            pickupCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        } while (sessionIdsByPickupCode.containsKey(pickupCode));
        return pickupCode;
    }
}
