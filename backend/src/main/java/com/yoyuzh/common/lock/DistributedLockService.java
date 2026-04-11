package com.yoyuzh.common.lock;

import java.time.Duration;
import java.util.function.Supplier;

public interface DistributedLockService {

    <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action);

    default void runWithLock(String lockName, Duration ttl, Runnable action) {
        executeWithLock(lockName, ttl, () -> {
            action.run();
            return null;
        });
    }

    static DistributedLockService noOp() {
        return NoOpHolder.INSTANCE;
    }

    final class NoOpHolder {
        private static final DistributedLockService INSTANCE = new DistributedLockService() {
            @Override
            public <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action) {
                return action.get();
            }
        };

        private NoOpHolder() {
        }
    }
}
