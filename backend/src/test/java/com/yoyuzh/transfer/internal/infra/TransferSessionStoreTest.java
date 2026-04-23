package com.yoyuzh.transfer.internal.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.infra.lock.DistributedLockGateway;
import com.yoyuzh.transfer.api.TransferFileItem;
import com.yoyuzh.transfer.internal.domain.TransferSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferSessionStoreTest {

    @Test
    void shouldRoundTripSessionThroughRedisWhenEnabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AppRedisProperties redisProperties = new AppRedisProperties();
        redisProperties.setEnabled(true);
        redisProperties.setTtlBufferSeconds(30);

        TransferSessionStore store = new TransferSessionStore(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                redisProperties,
                DistributedLockGateway.noOp()
        );

        TransferSession session = new TransferSession(
                "session-1",
                "123456",
                Instant.now().plusSeconds(300),
                List.of(new TransferFileItem("demo.txt", 12, "text/plain"))
        );

        store.save(session);

        ArgumentCaptor<String> sessionJson = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("yoyuzh:transfer-sessions:session:session-1"), sessionJson.capture(), any(Duration.class));
        verify(valueOperations).set(eq("yoyuzh:transfer-sessions:pickup:123456"), eq("session-1"), any(Duration.class));

        when(valueOperations.get("yoyuzh:transfer-sessions:pickup:123456")).thenReturn("session-1");
        when(valueOperations.get("yoyuzh:transfer-sessions:session:session-1")).thenReturn(sessionJson.getValue());

        TransferSession reloaded = store.findByPickupCode("123456").orElseThrow();

        assertThat(reloaded.toSessionResponse().sessionId()).isEqualTo("session-1");
        assertThat(reloaded.toLookupResponse().pickupCode()).isEqualTo("123456");
    }
}
