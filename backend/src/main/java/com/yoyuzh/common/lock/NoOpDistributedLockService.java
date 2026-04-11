package com.yoyuzh.common.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedLockService implements DistributedLockService {

    @Override
    public <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action) {
        return action.get();
    }
}
