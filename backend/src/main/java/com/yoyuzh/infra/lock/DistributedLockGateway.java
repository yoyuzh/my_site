package com.yoyuzh.infra.lock;

import java.time.Duration;
import java.util.function.Supplier;

public interface DistributedLockGateway {

    <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action);

    default void runWithLock(String lockName, Duration ttl, Runnable action) {
        executeWithLock(lockName, ttl, () -> {
            action.run();
            return null;
        });
    }

    static DistributedLockGateway noOp() {
        return new DistributedLockGateway() {
            @Override
            public <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action) {
                return action.get();
            }
        };
    }
}
