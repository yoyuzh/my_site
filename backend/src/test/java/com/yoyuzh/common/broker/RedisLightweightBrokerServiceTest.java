package com.yoyuzh.common.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.config.AppRedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLightweightBrokerServiceTest {

    @Test
    void shouldSkipMalformedRawPayloadAndContinuePollingQueue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("yoyuzh:broker:media-metadata-trigger:queue"))
                .thenReturn("{bad-json")
                .thenReturn("{\"userId\":7,\"fileId\":11,\"correlationId\":\"media-meta:auto:file:11\"}");

        RedisLightweightBrokerService service = new RedisLightweightBrokerService(
                redisTemplate,
                new ObjectMapper(),
                new AppRedisProperties()
        );

        Optional<Map<String, Object>> result = service.poll("media-metadata-trigger");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).containsEntry("userId", 7);
        assertThat(result.orElseThrow()).containsEntry("fileId", 11);
        assertThat(result.orElseThrow()).containsEntry("correlationId", "media-meta:auto:file:11");
        verify(listOperations, times(2)).leftPop("yoyuzh:broker:media-metadata-trigger:queue");
    }
}
