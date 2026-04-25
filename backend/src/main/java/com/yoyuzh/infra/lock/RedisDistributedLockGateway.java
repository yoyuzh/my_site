package com.yoyuzh.infra.lock;

import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisDistributedLockGateway implements DistributedLockGateway {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );
    private static final Duration LOCK_ACQUIRE_TIMEOUT = Duration.ofSeconds(1);
    private static final long LOCK_RETRY_INTERVAL_MILLIS = 40L;

    private final StringRedisTemplate stringRedisTemplate;
    private final AppRedisProperties redisProperties;

    public RedisDistributedLockGateway(StringRedisTemplate stringRedisTemplate,
                                       AppRedisProperties redisProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
    }

    @Override
    public <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action) {
        if (!StringUtils.hasText(lockName)) {
            return action.get();
        }

        String key = buildLockKey(lockName);
        String ownerToken = UUID.randomUUID().toString();
        Duration effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                ? Duration.ofSeconds(60)
                : ttl;
        if (!tryAcquireLock(key, ownerToken, effectiveTtl)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "操作正在处理中，请稍后重试");
        }

        try {
            return action.get();
        } finally {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
        }
    }

    private String buildLockKey(String lockName) {
        return redisProperties.getKeyPrefix()
                + ":" + redisProperties.getNamespaces().getLocks()
                + ":" + lockName.trim();
    }

    private boolean tryAcquireLock(String key, String ownerToken, Duration effectiveTtl) {
        long deadlineNanos = System.nanoTime() + LOCK_ACQUIRE_TIMEOUT.toNanos();
        do {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, ownerToken, effectiveTtl);
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            LockSupport.parkNanos(Duration.ofMillis(LOCK_RETRY_INTERVAL_MILLIS).toNanos());
        } while (System.nanoTime() < deadlineNanos);
        return false;
    }
}
