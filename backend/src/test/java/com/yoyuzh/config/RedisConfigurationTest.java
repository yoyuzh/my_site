package com.yoyuzh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.core.FileMetadataResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigurationTest {

    @Test
    void redisValueSerializerShouldHandleJavaTimeTypes() {
        RedisSerializer<Object> serializer = RedisConfiguration.redisValueSerializer(
                new ObjectMapper().findAndRegisterModules()
        );

        byte[] serialized = serializer.serialize(new TestPage(
                List.of(new FileMetadataResponse(1L, "notes.txt", "/docs", 12L, "text/plain", false,
                        LocalDateTime.of(2026, 4, 10, 18, 30)))
        ));
        Object restored = serializer.deserialize(serialized);
        TestPage restoredPage = new ObjectMapper()
                .findAndRegisterModules()
                .convertValue(restored, TestPage.class);

        assertThat(serialized).isNotNull();
        assertThat(restoredPage).isEqualTo(new TestPage(
                List.of(new FileMetadataResponse(1L, "notes.txt", "/docs", 12L, "text/plain", false,
                        LocalDateTime.of(2026, 4, 10, 18, 30)))
        ));
    }

    private record TestPage(List<FileMetadataResponse> items) {
    }
}
