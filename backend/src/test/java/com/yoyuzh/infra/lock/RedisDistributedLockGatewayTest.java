package com.yoyuzh.infra.lock;

import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDistributedLockGatewayTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisDistributedLockGateway gateway;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        gateway = new RedisDistributedLockGateway(stringRedisTemplate, new AppRedisProperties());
    }

    @Test
    void shouldRetryBrieflyBeforeAcquiringLock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false, false, true);
        when(stringRedisTemplate.execute(any(), any(List.class), anyString())).thenReturn(1L);

        String result = gateway.executeWithLock("transfer-session:demo", Duration.ofSeconds(5), () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(valueOperations, times(3)).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(5)));
        verify(stringRedisTemplate).execute(any(), any(List.class), anyString());
    }

    @Test
    void shouldThrowWhenLockCannotBeAcquiredWithinRetryWindow() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> gateway.executeWithLock("transfer-session:demo", Duration.ofSeconds(5), () -> "ok"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作正在处理中");

        verify(stringRedisTemplate, never()).execute(any(), any(List.class), anyString());
    }
}
