package com.yoyuzh.files.workspace.internal.infra;

import com.yoyuzh.files.workspace.internal.application.*;
import com.yoyuzh.files.workspace.internal.domain.*;
import com.yoyuzh.files.workspace.internal.infra.*;
import com.yoyuzh.files.workspace.internal.web.*;
import com.yoyuzh.files.content.internal.application.*;
import com.yoyuzh.files.content.internal.domain.*;
import com.yoyuzh.files.content.internal.infra.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.Cursor.CursorId;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RedisFileListDirectoryCacheServiceTest {

    private ConcurrentMapCacheManager cacheManager;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisFileListDirectoryCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(RedisCacheNames.FILES_LIST);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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
                        "createdAt", List.of(2026, 4, 10, 18, 30),
                        "hasChildDirectory", false
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
                LocalDateTime.of(2026, 4, 10, 18, 30),
                LocalDateTime.of(2026, 4, 10, 18, 30),
                false
        ));
    }

    @Test
    void shouldEvictCachedPagesWhenDirectoryIsTouched() {
        Cursor<String> cursor = new ListCursor(List.of(
                "yoyuzh:cache:files:list:u:7:path:L2RvY3M:page:0:size:30:sort:directory-desc-created-desc:v:0",
                "yoyuzh:cache:files:list:u:7:path:L2RvY3M:page:1:size:30:sort:directory-desc-created-desc:v:0"
        ));
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        cacheService.touchDirectory(7L, "/docs");

        verify(valueOperations).increment("yoyuzh:cache:files-list:version:u:7:path:L2RvY3M");
        verify(stringRedisTemplate).expire(eq("yoyuzh:cache:files-list:version:u:7:path:L2RvY3M"), any(Duration.class));
        verify(stringRedisTemplate).delete(List.of(
                "yoyuzh:cache:files:list:u:7:path:L2RvY3M:page:0:size:30:sort:directory-desc-created-desc:v:0",
                "yoyuzh:cache:files:list:u:7:path:L2RvY3M:page:1:size:30:sort:directory-desc-created-desc:v:0"
        ));
    }

    @Test
    void shouldTouchDirectoryOnlyAfterTransactionCommits() {
        Cursor<String> cursor = new ListCursor(List.of());
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cacheService.touchDirectory(7L, "/docs");

            verify(valueOperations, never()).increment(anyString());
            verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronizationUtils.triggerAfterCommit();

            verify(valueOperations).increment("yoyuzh:cache:files-list:version:u:7:path:L2RvY3M");
            verify(stringRedisTemplate).expire(eq("yoyuzh:cache:files-list:version:u:7:path:L2RvY3M"), any(Duration.class));
        } finally {
            TransactionSynchronizationUtils.triggerAfterCompletion(0);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static final class ListCursor implements Cursor<String> {
        private final Iterator<String> iterator;
        private long position;
        private boolean closed;

        private ListCursor(List<String> values) {
            this.iterator = values.iterator();
        }

        @Override
        public CursorId getId() {
            return CursorId.of(0);
        }

        @Override
        public long getCursorId() {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public String next() {
            position += 1;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
