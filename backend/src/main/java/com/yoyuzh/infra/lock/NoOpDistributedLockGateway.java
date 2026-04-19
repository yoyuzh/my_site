package com.yoyuzh.infra.lock;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedLockGateway implements DistributedLockGateway {

    @Override
    public <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action) {
        return action.get();
    }
}
