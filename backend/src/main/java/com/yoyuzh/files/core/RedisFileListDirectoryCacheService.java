package com.yoyuzh.files.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisFileListDirectoryCacheService implements FileListDirectoryCacheService {

    private static final String SORT_CONTEXT = "directory-desc-created-desc";

    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppRedisProperties redisProperties;
    private final ObjectMapper objectMapper;

    public RedisFileListDirectoryCacheService(CacheManager cacheManager,
                                              StringRedisTemplate stringRedisTemplate,
                                              AppRedisProperties redisProperties,
                                              ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<FileMetadataResponse> getOrLoad(Long userId,
                                                        String path,
                                                        int page,
                                                        int size,
                                                        Supplier<PageResponse<FileMetadataResponse>> loader) {
        Cache cache = cacheManager.getCache(RedisCacheNames.FILES_LIST);
        if (cache == null) {
            return loader.get();
        }

        long version = readDirectoryVersion(userId, path);
        String cacheKey = buildCacheKey(userId, path, page, size, version);
        CachedFileListPage cached = readCachedPage(cache, cacheKey);
        if (cached != null) {
            return cached.toPageResponse();
        }

        PageResponse<FileMetadataResponse> loaded = loader.get();
        cache.put(cacheKey, CachedFileListPage.from(loaded));
        return loaded;
    }

    @Override
    public void touchDirectories(Long userId, Collection<String> paths) {
        if (userId == null || paths == null || paths.isEmpty()) {
            return;
        }

        Set<String> normalizedPaths = new LinkedHashSet<>();
        for (String path : paths) {
            String normalized = normalizeDirectoryPath(path);
            if (normalized != null) {
                normalizedPaths.add(normalized);
            }
        }
        if (normalizedPaths.isEmpty()) {
            return;
        }

        Duration ttl = Duration.ofSeconds(Math.max(
                redisProperties.getCache().getDirectoryVersionTtlSeconds(),
                redisProperties.getCache().getFilesListTtlSeconds() * 2
        ));
        for (String path : normalizedPaths) {
            String key = buildDirectoryVersionKey(userId, path);
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, ttl);
        }
    }

    private CachedFileListPage readCachedPage(Cache cache, String cacheKey) {
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        if (wrapper == null || wrapper.get() == null) {
            return null;
        }
        Object cachedValue = wrapper.get();
        if (cachedValue instanceof CachedFileListPage cachedFileListPage) {
            return cachedFileListPage;
        }
        return objectMapper.convertValue(cachedValue, CachedFileListPage.class);
    }

    private long readDirectoryVersion(Long userId, String path) {
        String value = stringRedisTemplate.opsForValue().get(buildDirectoryVersionKey(userId, path));
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String buildCacheKey(Long userId, String path, int page, int size, long version) {
        return "u:" + userId
                + ":path:" + encode(path)
                + ":page:" + page
                + ":size:" + size
                + ":sort:" + SORT_CONTEXT
                + ":v:" + version;
    }

    private String buildDirectoryVersionKey(Long userId, String path) {
        return redisProperties.getKeyPrefix()
                + ":" + redisProperties.getNamespaces().getCache()
                + ":files-list:version:u:" + userId
                + ":path:" + encode(path);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeDirectoryPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.trim().replace("\\", "/");
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record CachedFileListPage(List<FileMetadataResponse> items, long total, int page, int size) {
        private static CachedFileListPage from(PageResponse<FileMetadataResponse> response) {
            return new CachedFileListPage(response.items(), response.total(), response.page(), response.size());
        }

        private PageResponse<FileMetadataResponse> toPageResponse() {
            return new PageResponse<>(items, total, page, size);
        }
    }
}
