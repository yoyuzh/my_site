package com.yoyuzh.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

class RedisConfigurationTest {

    @Test
    void redisValueSerializerShouldHandleJavaTimeTypes() {
        RedisSerializer<Object> serializer = RedisConfiguration.redisValueSerializer(
                new ObjectMapper().findAndRegisterModules()
        );

        byte[] serialized = serializer.serialize(
                new TestPage(List.of(new FileMetadataResponse(
                        1L,
                        "notes.txt",
                        "/docs",
                        12L,
                        "text/plain",
                        false,
                        LocalDateTime.of(2026, 4, 10, 18, 30),
                        LocalDateTime.of(2026, 4, 10, 18, 30),
                        false
                )))
        );
        Object restored = serializer.deserialize(serialized);
        TestPage restoredPage = new ObjectMapper()
                .findAndRegisterModules()
                .convertValue(restored, TestPage.class);

        assertThat(serialized).isNotNull();
        assertThat(restoredPage).isEqualTo(new TestPage(
                List.of(new FileMetadataResponse(
                        1L,
                        "notes.txt",
                        "/docs",
                        12L,
                        "text/plain",
                        false,
                        LocalDateTime.of(2026, 4, 10, 18, 30),
                        LocalDateTime.of(2026, 4, 10, 18, 30),
                        false
                ))
        ));
    }

    private record TestPage(List<FileMetadataResponse> items) {
    }
}
