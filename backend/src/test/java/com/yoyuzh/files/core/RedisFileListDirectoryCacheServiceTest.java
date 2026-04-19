package com.yoyuzh.files.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisFileListDirectoryCacheServiceTest {

    private ConcurrentMapCacheManager cacheManager;
    private StringRedisTemplate stringRedisTemplate;
    private RedisFileListDirectoryCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(RedisCacheNames.FILES_LIST);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(stringRedisTemplate.opsForValue().get(anyString())).thenReturn(null);

        AppRedisProperties redisProperties = new AppRedisProperties();
        cacheService = new RedisFileListDirectoryCacheService(
                cacheManager,
                stringRedisTemplate,
                redisProperties,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void shouldReadCachedPageStoredAsGenericMap() {
        Cache cache = cacheManager.getCache(RedisCacheNames.FILES_LIST);
        cache.put("u:7:path:Lw:page:0:size:10:sort:directory-desc-created-desc:v:0", Map.of(
                "items", List.of(Map.of(
                        "id", 1L,
                        "filename", "notes.txt",
                        "path", "/docs",
                        "size", 12L,
                        "contentType", "text/plain",
                        "directory", false,
                        "createdAt", List.of(2026, 4, 10, 18, 30)
                )),
                "total", 1L,
                "page", 0,
                "size", 10
        ));

        PageResponse<FileMetadataResponse> result = cacheService.getOrLoad(7L, "/", 0, 10,
                () -> new PageResponse<>(List.of(), 0, 0, 10));

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).containsExactly(new FileMetadataResponse(
                1L,
                "notes.txt",
                "/docs",
                12L,
                "text/plain",
                false,
                LocalDateTime.of(2026, 4, 10, 18, 30)
        ));
    }
}
