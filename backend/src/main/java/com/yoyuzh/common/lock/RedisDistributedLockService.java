package com.yoyuzh.common.lock;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.AppRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisDistributedLockService implements DistributedLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final AppRedisProperties redisProperties;

    public RedisDistributedLockService(StringRedisTemplate stringRedisTemplate,
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
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, ownerToken, effectiveTtl);
        if (!Boolean.TRUE.equals(acquired)) {
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
}
